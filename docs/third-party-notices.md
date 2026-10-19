# Third-party notices

The Learning Portal distributes the following third-party components. Full licence texts of Maven and npm
dependencies are available from their published artifacts; versions are listed in
[dependencies.md](dependencies.md).

## Bundled font

Certificates embed **Noto Sans** (Regular and Bold), Copyright 2022 The Noto Project Authors
(https://github.com/notofonts/latin-greek-cyrillic), licensed under the **SIL Open Font License, Version 1.1**.
The licence text ships beside the font files at
`backend/src/main/resources/fonts/NotoSans-OFL.txt`.

Noto Sans covers Latin, Greek and Cyrillic. Certificates for names in scripts it does not cover, or that need complex
shaping (for example Bengali), fail with an explicit error rather than printing a damaged name. Supporting such
scripts needs a shaping-capable renderer and is not part of release 1.

## Libraries with notable licences

| Component | Licence |
| --- | --- |
| Apache PDFBox | Apache-2.0 |
| ZXing core (QR codes) | Apache-2.0 |
| OWASP Java HTML Sanitizer | Apache-2.0 |
| Apache Commons CSV | Apache-2.0 |
| Spring Boot, Spring Framework, Spring Security, Spring Session | Apache-2.0 |
| Hibernate ORM | Apache-2.0 |
| PostgreSQL JDBC driver | BSD-2-Clause |
| React, React Router, TanStack Query, TipTap | MIT |
