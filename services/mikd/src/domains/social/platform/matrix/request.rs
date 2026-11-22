use backon::{ExponentialBuilder, Retryable};
use matrix_sdk::ruma::api::error::{ErrorKind, RetryAfter};
use matrix_sdk::{Error, HttpError};
use std::{
    future::Future,
    time::{Duration, SystemTime},
};

pub(super) async fn retry<T, E, Fut: Future<Output = Result<T, E>>>(
    operation: impl FnMut() -> Fut,
    http: impl Fn(&E) -> Option<&HttpError>,
) -> Result<T, E> {
    let backoff = ExponentialBuilder::default()
        .with_min_delay(Duration::from_secs(1))
        .with_max_delay(Duration::from_secs(30))
        .without_max_times();
    let mut warned = false;
    operation
        .retry(backoff)
        .when(|error| http(error).is_some_and(transient))
        .adjust(|error, delay| {
            delay.map(|delay| delay.max(http(error).and_then(retry_after).unwrap_or_default()))
        })
        .notify(|_, _| {
            if !warned {
                eprintln!("mikd Matrix provisioning request unavailable; retrying with backoff");
                warned = true;
            }
        })
        .await
}

pub(super) fn sdk_http(error: &Error) -> Option<&HttpError> {
    match error {
        Error::Http(error) => Some(error),
        _ => None,
    }
}

fn underlying(error: &HttpError) -> &HttpError {
    match error {
        HttpError::Cached(inner) => underlying(inner),
        _ => error,
    }
}

fn transient(error: &HttpError) -> bool {
    let error = underlying(error);
    if let HttpError::Reqwest(error) = error {
        return error.is_connect() || error.is_timeout() || error.is_body();
    }
    error.as_client_api_error().is_some_and(|error| {
        matches!(error.status_code.as_u16(), 408 | 429 | 500..=599)
            || matches!(error.error_kind(), Some(ErrorKind::LimitExceeded(_)))
    })
}

pub(super) fn retry_after(error: &HttpError) -> Option<Duration> {
    let ErrorKind::LimitExceeded(limit) = underlying(error).client_api_error_kind()? else {
        return None;
    };
    match limit.retry_after? {
        RetryAfter::Delay(delay) => Some(delay),
        RetryAfter::DateTime(time) => time.duration_since(SystemTime::now()).ok(),
    }
}
