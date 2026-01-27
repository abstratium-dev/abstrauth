---
trigger: glob
description: Working on files in src/main/webui or when working on styles
globs: src/main/webui/**/*.scss
---

CSS / SCSS styles should normally be added to the global file `src/main/webui/src/styles.scss` rather than in individual angular component style files, as they can be reused by other components. It is important not to duplicate styles that already exist in the global styles file.