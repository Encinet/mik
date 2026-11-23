//! Public HTTP connections with address filtering applied inside the actual resolver.
use reqwest::{
    ClientBuilder, Url,
    dns::{Addrs, Name, Resolve, Resolving},
    redirect::Policy,
};
use rustls::crypto::{CryptoProvider, ring};
use std::{io, net::IpAddr, sync::Arc, time::Duration};

pub(crate) fn ensure_tls_provider() {
    // Required by reqwest's rustls-no-provider feature, including inside Matrix.
    if CryptoProvider::get_default().is_none() {
        // A concurrent client may already have installed the same provider.
        drop(ring::default_provider().install_default());
    }
}

pub(crate) fn client_builder() -> ClientBuilder {
    ensure_tls_provider();
    reqwest::Client::builder()
}

pub(crate) fn direct_client(allow_private: bool) -> ClientBuilder {
    client_builder()
        .no_proxy()
        .dns_resolver(Arc::new(Dns { allow_private }))
        .redirect(Policy::none())
        .pool_max_idle_per_host(1)
        .pool_idle_timeout(Duration::from_secs(20))
}

struct Dns {
    allow_private: bool,
}
impl Resolve for Dns {
    fn resolve(&self, name: Name) -> Resolving {
        let allow_private = self.allow_private;
        Box::pin(async move {
            let addresses: Vec<_> = tokio::net::lookup_host((name.as_str(), 0)).await?.collect();
            if addresses.is_empty()
                || (!allow_private && addresses.iter().any(|address| !public_ip(address.ip())))
            {
                return Err(io::Error::new(
                    io::ErrorKind::PermissionDenied,
                    "no permitted HTTP destination",
                )
                .into());
            }
            Ok(Box::new(addresses.into_iter()) as Addrs)
        })
    }
}

fn public_ip(ip: IpAddr) -> bool {
    match ip.to_canonical() {
        IpAddr::V4(ip) => {
            let [a, b, c, _] = ip.octets();
            !matches!(a, 0 | 10 | 127 | 224..=255)
                && !matches!(
                    (a, b),
                    (100, 64..=127) | (169, 254) | (172, 16..=31) | (192, 168) | (198, 18 | 19)
                )
                && !matches!(
                    (a, b, c),
                    (192, 0, 0 | 2) | (192, 88, 99) | (198, 51, 100) | (203, 0, 113)
                )
        }
        IpAddr::V6(ip) => {
            let [a, b, ..] = ip.segments();
            (0x2000..=0x3fff).contains(&a)
                && !matches!((a, b), (0x2001, 0..=0x1ff | 0xdb8) | (0x2002, _))
                && a != 0x3fff
        }
    }
}

pub(crate) fn allowed_url(url: &Url, allow_private: bool) -> bool {
    matches!(url.scheme(), "http" | "https")
        && url.username().is_empty()
        && url.password().is_none()
        && url.port() != Some(0)
        && match url.host() {
            Some(url::Host::Domain(host)) => {
                let host = host.trim_end_matches('.');
                allow_private
                    || (host != "localhost"
                        && ![
                            ".localhost",
                            ".local",
                            ".internal",
                            ".localdomain",
                            ".home",
                            ".lan",
                            ".onion",
                            ".invalid",
                        ]
                        .iter()
                        .any(|suffix| host.ends_with(suffix)))
            }
            Some(url::Host::Ipv4(ip)) => allow_private || public_ip(ip.into()),
            Some(url::Host::Ipv6(ip)) => allow_private || public_ip(ip.into()),
            None => false,
        }
}

pub(crate) fn parse_url(value: &str, allow_private: bool) -> Option<Url> {
    if value.len() > 4096 || value.contains('\\') || value.chars().any(char::is_control) {
        return None;
    }
    let mut url = Url::parse(value).ok()?;
    url.set_fragment(None);
    allowed_url(&url, allow_private).then_some(url)
}
