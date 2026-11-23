#![allow(
    clippy::needless_pass_by_value,
    reason = "QuickJS host callbacks receive owned JS values"
)]

use aes::{
    Aes128, Aes192, Aes256,
    cipher::{BlockModeEncrypt, KeyInit, KeyIvInit, StreamCipher, block_padding::Pkcs7},
};
use base64::{Engine, engine::general_purpose::STANDARD};
use flate2::{Compression, read::ZlibDecoder, write::ZlibEncoder};
use md5::{Digest, Md5};
use rquickjs::{Ctx, Exception, Object, Result, TypedArray, function::Func};
use rsa::{
    BoxedUint, PublicKey, hazmat::rsa_encrypt, pkcs8::DecodePublicKey, traits::PublicKeyParts,
};
use std::io::{Read, Write};

pub(super) const MAX_BYTES: usize = 4 * 1024 * 1024;

pub(super) fn install(host: &Object<'_>) -> Result<()> {
    host.set("maxBytes", MAX_BYTES)?;
    host.set("bufferFrom", Func::from(buffer_from))?;
    host.set("bufferToString", Func::from(buffer_to_string))?;
    host.set(
        "md5",
        Func::from(|text: String| hex::encode(Md5::digest(text))),
    )?;
    host.set("randomBytes", Func::from(random_bytes))?;
    host.set("aes", Func::from(aes))?;
    host.set("rsa", Func::from(rsa))?;
    host.set("compress", Func::from(compress))?;
    Ok(())
}

fn input(ctx: &Ctx<'_>, value: &str) -> Result<Vec<u8>> {
    let error = || Exception::throw_message(ctx, "invalid or oversized LX byte array");
    if value.len() > 4 * MAX_BYTES.div_ceil(3) {
        return Err(error());
    }
    STANDARD
        .decode(value)
        .ok()
        .filter(|bytes| bytes.len() <= MAX_BYTES)
        .ok_or_else(error)
}

fn buffer_from(ctx: Ctx<'_>, value: String, encoding: String) -> Result<TypedArray<'_, u8>> {
    let bytes = match encoding.to_ascii_lowercase().as_str() {
        "hex" => hex::decode(value).map_err(|_| Exception::throw_message(&ctx, "invalid hex"))?,
        "base64" => STANDARD
            .decode(value)
            .map_err(|_| Exception::throw_message(&ctx, "invalid Base64"))?,
        "binary" | "latin1" => value
            .chars()
            .map(|ch| u8::try_from(u32::from(ch)).unwrap_or(b'?'))
            .collect(),
        _ => value.into_bytes(),
    };
    TypedArray::new(ctx, bytes)
}

fn buffer_to_string(ctx: Ctx<'_>, value: String, encoding: String) -> Result<String> {
    let bytes = input(&ctx, &value)?;
    Ok(match encoding.to_ascii_lowercase().as_str() {
        "hex" => hex::encode(bytes),
        "base64" => STANDARD.encode(bytes),
        "binary" | "latin1" => bytes.iter().copied().map(char::from).collect(),
        _ => String::from_utf8_lossy(&bytes).into_owned(),
    })
}

fn random_bytes(ctx: Ctx<'_>, size: u32) -> Result<TypedArray<'_, u8>> {
    let mut bytes = vec![0; size.min(65536) as usize];
    getrandom::fill(&mut bytes)
        .map_err(|_| Exception::throw_message(&ctx, "randomness unavailable"))?;
    TypedArray::new(ctx, bytes)
}

fn aes(
    ctx: Ctx<'_>,
    value: String,
    mode: String,
    key: String,
    iv: String,
) -> Result<TypedArray<'_, u8>> {
    let value = input(&ctx, &value)?;
    let key = input(&ctx, &key)?;
    let iv = input(&ctx, &iv)?;
    let mode = mode.to_ascii_lowercase();
    macro_rules! encrypt {
        ($cipher:ty) => {{
            if mode.contains("ecb") {
                ecb::Encryptor::<$cipher>::new_from_slice(&key)
                    .map(|cipher| cipher.encrypt_padded_vec::<Pkcs7>(&value))
            } else if mode.contains("ctr") {
                ctr::Ctr128BE::<$cipher>::new_from_slices(&key, &iv).map(|mut cipher| {
                    let mut bytes = value;
                    cipher.apply_keystream(&mut bytes);
                    bytes
                })
            } else {
                cbc::Encryptor::<$cipher>::new_from_slices(&key, &iv)
                    .map(|cipher| cipher.encrypt_padded_vec::<Pkcs7>(&value))
            }
        }};
    }
    let encrypted = match key.len() {
        16 => encrypt!(Aes128),
        24 => encrypt!(Aes192),
        32 => encrypt!(Aes256),
        _ => return Err(Exception::throw_message(&ctx, "invalid AES key")),
    }
    .map_err(|_| Exception::throw_message(&ctx, "invalid AES key or IV"))?;
    TypedArray::new(ctx, encrypted)
}

fn rsa(ctx: Ctx<'_>, value: String, key: String) -> Result<TypedArray<'_, u8>> {
    let key = (key.len() <= 4096)
        .then(|| PublicKey::from_public_key_pem(&key).ok())
        .flatten()
        .ok_or_else(|| Exception::throw_message(&ctx, "invalid RSA key"))?;
    let value = input(&ctx, &value)?;
    let data = BoxedUint::from_be_slice_vartime(&value[value.len().saturating_sub(128)..]);
    let encrypted = rsa_encrypt(&key, &data)
        .map_err(|_| Exception::throw_message(&ctx, "RSA operation failed"))?;
    let bytes = encrypted.to_be_bytes();
    let mut result = vec![0; key.size()];
    let offset = result.len().saturating_sub(bytes.len());
    let start = bytes.len().saturating_sub(result.len());
    result[offset..].copy_from_slice(&bytes[start..]);
    TypedArray::new(ctx, result)
}

fn compress(ctx: Ctx<'_>, value: String, inflate: bool) -> Result<TypedArray<'_, u8>> {
    let bytes = input(&ctx, &value)?;
    let error = |_| Exception::throw_message(&ctx, "invalid zlib data");
    let result = if inflate {
        let mut result = Vec::new();
        ZlibDecoder::new(bytes.as_slice())
            .take((MAX_BYTES + 1) as u64)
            .read_to_end(&mut result)
            .map_err(error)?;
        if result.len() > MAX_BYTES {
            return Err(Exception::throw_message(
                &ctx,
                "LX inflate output too large",
            ));
        }
        result
    } else {
        let mut encoder = ZlibEncoder::new(Vec::new(), Compression::fast());
        encoder.write_all(&bytes).map_err(error)?;
        encoder.finish().map_err(error)?
    };
    TypedArray::new(ctx, result)
}
