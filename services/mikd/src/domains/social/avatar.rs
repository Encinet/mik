use image::{ImageFormat, ImageReader, Limits, RgbaImage, imageops};
use reqwest::{Client, redirect::Policy};
use std::{io::Cursor, sync::LazyLock, time::Duration};
use url::Url;

pub(super) const SIZE: u32 = 64;
const MAX_BYTES: usize = 1024 * 1024;

static HTTP: LazyLock<reqwest::Result<Client>> = LazyLock::new(|| {
    crate::http_egress::client_builder()
        .redirect(Policy::none())
        .timeout(Duration::from_secs(2))
        .build()
});

pub(super) async fn fetch(texture: &str) -> Option<Vec<u8>> {
    let url = Url::parse(texture).ok()?;
    let hash = url.path().strip_prefix("/texture/")?;
    if url.host_str() != Some("textures.minecraft.net")
        || !(32..=128).contains(&hash.len())
        || !hash.bytes().all(|byte| byte.is_ascii_hexdigit())
    {
        return None;
    }
    let mut response = HTTP
        .as_ref()
        .ok()?
        .get(format!("https://textures.minecraft.net/texture/{hash}"))
        .send()
        .await
        .ok()?;
    if !response.status().is_success()
        || response
            .content_length()
            .is_some_and(|size| size > MAX_BYTES as u64)
    {
        return None;
    }
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.ok()? {
        if bytes.len() + chunk.len() > MAX_BYTES {
            return None;
        }
        bytes.extend_from_slice(&chunk);
    }
    tokio::task::spawn_blocking(move || render(&bytes))
        .await
        .ok()?
}

fn render(png: &[u8]) -> Option<Vec<u8>> {
    let mut reader = ImageReader::with_format(Cursor::new(png), ImageFormat::Png);
    let mut limits = Limits::default();
    limits.max_image_width = Some(64);
    limits.max_image_height = Some(64);
    reader.limits(limits);
    let skin = reader.decode().ok()?.into_rgba8();
    if !matches!(skin.dimensions(), (64, 32 | 64)) {
        return None;
    }

    let layer = |left, size| {
        imageops::resize(
            &imageops::crop_imm(&skin, left, 8, 8, 8).to_image(),
            size,
            size,
            imageops::FilterType::Nearest,
        )
    };
    let mut face = RgbaImage::new(SIZE, SIZE);
    imageops::overlay(&mut face, &layer(8, 56), 4, 4);
    imageops::overlay(&mut face, &layer(40, SIZE), 0, 0);
    let mut output = Cursor::new(Vec::new());
    face.write_to(&mut output, ImageFormat::Png).ok()?;
    Some(output.into_inner())
}
