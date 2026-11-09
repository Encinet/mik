package org.encinet.mik.shared.menu.image;

import java.net.URI;
import java.util.Objects;

/** A client-accessible image and its original pixel dimensions, used for proportional layout. */
public record UiImage(URI url, int pixelWidth, int pixelHeight) {
    public UiImage {
        Objects.requireNonNull(url, "url");
        if (!("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
                || url.getHost() == null || url.getRawUserInfo() != null || url.getFragment() != null)
            throw new IllegalArgumentException("Images require an HTTP(S) URL without credentials or a fragment");
        if (url.toASCIIString().length() > 8192) throw new IllegalArgumentException("Image URL is too long");
        if (pixelWidth < 1 || pixelHeight < 1) throw new IllegalArgumentException("Image dimensions must be positive");
    }

    public static UiImage remote(String url, int pixelWidth, int pixelHeight) {
        return new UiImage(URI.create(url), pixelWidth, pixelHeight);
    }

    public double aspectRatio() { return (double) pixelWidth / pixelHeight; }
}
