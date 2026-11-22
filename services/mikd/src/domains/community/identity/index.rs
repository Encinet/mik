use super::{Binding, ExternalKey, SELECT_BINDING, binding_row};
use crate::domains::community::Error;
use std::{
    collections::{BTreeSet, HashMap},
    sync::{Arc, RwLock},
};
use tokio_rusqlite::rusqlite::{Connection, Transaction};
use uuid::Uuid;

#[derive(Default)]
pub(crate) struct BindingIndex(RwLock<Index>);

#[derive(Default)]
pub(super) struct Index {
    records: HashMap<Uuid, Arc<Binding>>,
    external: HashMap<ExternalKey, Uuid>,
    accounts: HashMap<Uuid, BTreeSet<Uuid>>,
    names: HashMap<String, BTreeSet<Uuid>>,
}

impl BindingIndex {
    pub(in crate::domains::community) fn load(db: &Connection) -> Result<Self, Error> {
        let mut index = Index::default();
        for binding in db.prepare(SELECT_BINDING)?.query_map([], binding_row)? {
            index.insert(binding?);
        }
        Ok(Self(RwLock::new(index)))
    }

    pub(super) fn commit(
        &self,
        tx: Transaction<'_>,
        update: impl FnOnce(&mut Index),
    ) -> Result<(), Error> {
        tx.commit()?;
        update(
            &mut self
                .0
                .write()
                .unwrap_or_else(std::sync::PoisonError::into_inner),
        );
        Ok(())
    }

    pub(crate) fn find(&self, external: &ExternalKey) -> Option<Arc<Binding>> {
        let index = self.0.read().ok()?;
        index.records.get(index.external.get(external)?).cloned()
    }

    pub(crate) fn player(&self, account: Uuid) -> Vec<Arc<Binding>> {
        let Ok(index) = self.0.read() else {
            return Vec::new();
        };
        index
            .accounts
            .get(&account)
            .into_iter()
            .flatten()
            .filter_map(|id| index.records.get(id).cloned())
            .collect()
    }

    pub(crate) fn named(&self, name: &str) -> Option<Arc<Binding>> {
        let index = self.0.read().ok()?;
        let accounts = index.names.get(&name.to_ascii_lowercase())?;
        if accounts.len() != 1 {
            return None;
        }
        let id = index.accounts.get(accounts.first()?)?.first()?;
        index.records.get(id).cloned()
    }

    pub(crate) fn departures(
        &self,
        absent: impl Fn(&ExternalKey) -> bool,
    ) -> Vec<(Uuid, ExternalKey)> {
        let Ok(index) = self.0.read() else {
            return Vec::new();
        };
        index
            .records
            .values()
            .filter(|binding| absent(&binding.external))
            .take(128)
            .map(|binding| (binding.id, binding.external.clone()))
            .collect()
    }
}

impl Index {
    pub(super) fn insert(&mut self, binding: Binding) {
        self.names
            .entry(binding.player_name.to_ascii_lowercase())
            .or_default()
            .insert(binding.account);
        self.external.insert(binding.external.clone(), binding.id);
        self.accounts
            .entry(binding.account)
            .or_default()
            .insert(binding.id);
        self.records.insert(binding.id, Arc::new(binding));
    }

    pub(super) fn remove(&mut self, binding: &Binding) {
        self.external.remove(&binding.external);
        self.records.remove(&binding.id);
        if let Some(ids) = self.accounts.get_mut(&binding.account) {
            ids.remove(&binding.id);
            if ids.is_empty() {
                self.accounts.remove(&binding.account);
                self.remove_name(binding.account, &binding.player_name);
            }
        }
    }

    pub(super) fn update_name(&mut self, account: Uuid, name: &str) {
        let Some(ids) = self.accounts.get(&account) else {
            return;
        };
        let Some(previous) = ids
            .first()
            .and_then(|id| self.records.get(id))
            .map(|binding| binding.player_name.clone())
        else {
            return;
        };
        if previous == name {
            return;
        }
        for id in ids {
            if let Some(binding) = self.records.get_mut(id) {
                Arc::make_mut(binding).player_name = name.into();
            }
        }
        self.remove_name(account, &previous);
        self.names
            .entry(name.to_ascii_lowercase())
            .or_default()
            .insert(account);
    }

    fn remove_name(&mut self, account: Uuid, name: &str) {
        let key = name.to_ascii_lowercase();
        if let Some(ids) = self.names.get_mut(&key) {
            ids.remove(&account);
            if ids.is_empty() {
                self.names.remove(&key);
            }
        }
    }
}
