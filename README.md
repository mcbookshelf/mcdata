# mcdata

> A version-controlled archive of extracted Minecraft data for use with [Bookshelf](https://github.com/mcbookshelf/bookshelf).

## Overview

This repository contains data extracted from various versions of Minecraft.  
Each Git tag corresponds to a specific Minecraft version and includes only the generated data for that version.

## Usage

You can access the extracted files directly via GitHub’s raw URLs:

```
https://raw.githubusercontent.com/mcbookshelf/mcdata/v1/<version>/blocks/data.min.json
```

Replace `<version>` with the desired Minecraft version, e.g. `1.21.7`.

> [!NOTE]
> The `v1` branch and its data format have been available since Minecraft `1.21.6`.
> We strongly recommend using this version for the latest and most stable data.
> For older Minecraft versions, please refer to our legacy builds, which use tags without the `v1/` prefix.

## Credits
This project has taken inspiration from [Aeldrion/IrisDataGen](https://github.com/Aeldrion/IrisDataGen) and [misode/mcmeta](https://github.com/misode/mcmeta).
