# Bookshelf McData
> A version-controlled repository of Minecraft's extracted block shape data for use with [Bookshelf](https://github.com/mcbookshelf/Bookshelf).

## Overview
This repository extracts block shapes and block data from Minecraft into JSON files for each game version.
Each commit represents a different Minecraft version, tagged as `<version>`.

You can view the extracted block shapes for a specific version by accessing the following URL (replace <version> with the Minecraft version you're interested in):

```
https://raw.githubusercontent.com/mcbookshelf/Bookshelf-McData/<version>/blocks/shapes.min.json
```

Concerning the block data, before 1.21.5 only sounds are available:

```
https://raw.githubusercontent.com/Gunivers/Bookshelf-McData/<version>/blocks/sounds.min.json
```

In and after 1.21.5, more data are available such as hardness, blast resistance, etc.:

```
https://raw.githubusercontent.com/Gunivers/Bookshelf-McData/<version>/blocks/data.min.json
```

## Credits
This project has taken inspiration from [Aeldrion/IrisDataGen](https://github.com/Aeldrion/IrisDataGen) and [misode/mcmeta](https://github.com/misode/mcmeta).
