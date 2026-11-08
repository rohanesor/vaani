# Bundled fonts

Self-hosted so the Android app renders the same type as the web client with no network access.
All files are WOFF2 subsets copied unmodified from the Fontsource npm packages and are licensed under the
SIL Open Font License 1.1 (https://openfontlicense.org).

| Family | Package | Weights | Subsets |
|---|---|---|---|
| Plus Jakarta Sans | @fontsource/plus-jakarta-sans | 400, 500, 600, 700 | latin, latin-ext |
| Fraunces (variable, opsz) | @fontsource-variable/fraunces | 100–900, normal + italic | latin, latin-ext |
| Noto Sans Devanagari, Tamil, Telugu, Kannada, Malayalam, Bengali, Gujarati, Oriya | @fontsource/noto-sans-* | 400, 600 | the script subset only |

`../fonts.css` declares the faces with `unicode-range`, so only the files a screen needs are loaded.
Odia (Noto Sans Oriya) is Android-only: the web client does not ship it yet.

Icons are not a font. They are an inline SVG sprite in `../index.html`: Lucide 1.70.0 (ISC licence,
https://lucide.dev) plus two VAANI marks (`i-ai`, `i-voice`) drawn for this project.
