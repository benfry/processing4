# Processing URI Schema Definition

## Local File Schema
```
pde:///path/to/sketch.pde
```
Attention: The 3rd slash is import to trigger local files.

## Sketch Operations

### Create New Sketch
```
pde://sketch/new
```

### Load Base64 Encoded Sketch
```
pde://sketch/base64/<base64_encoded_content>
```
Optional query parameters:
- `data`: Comma-separated {File} that should be placed in the data folder
- `code`: Comma-separated {File} that should be placed in the code folder
- `pde`: Comma-separated {File} that should be placed in the sketch folder
- `mode`: Processing mode identifier, e.g. `processing.mode.android.AndroidMode` You can find this in the sketch.properties file when switching modes on a sketch

### Load Sketch from URL

```
pde://sketch/url/github.com/processing/processing-examples/raw/refs/heads/main/Basics/Arrays/Array/Array.pde
```
[Click to here to test](pde://sketch/url/github.com/processing/processing-examples/raw/refs/heads/main/Basics/Arrays/Array/Array.pde)

Supports the same query parameters as base64 endpoint.
Optional query parameters:
- `data`: Comma-separated {File} that should be placed in the data folder
- `code`: Comma-separated {File} that should be placed in the code folder
- `pde`: Comma-separated {File} that should be placed in the sketch folder
- `mode`: Processing mode identifier, e.g. `processing.mode.android.AndroidMode` You can find this in the sketch.properties file when switching modes on a sketch

Example with query parameters:

```
pde://sketch/url/github.com/processing/processing-examples/raw/refs/heads/main/Basics/Image/Alphamask/Alphamask.pde?data=data/moonwalk.jpg,data/mask.jpg,processing.org/img/processing-web.png
```
[Click to here to test](pde://sketch/url/github.com/processing/processing-examples/raw/refs/heads/main/Basics/Image/Alphamask/Alphamask.pde?data=data/moonwalk.jpg,data/mask.jpg,processing.org/img/processing-web.png)

#### A {File}
A {File} is a string that represents a file in the sketch and has a couple of options, it always starts with the filename followed by a colon, e.g. `file.pde:example.com/path/to/file.pde`. The following options are available:
- `example.com/path/to/file.pde`: A remote file that should be downloaded
- `file.pde`: A remote file that should be downloaded with a path relative to the sketch (only available in loading the sketch from url)
- a base64 encoded file: A base64 encoded file

## Preferences
```
pde://preferences?key1=value1&key2=value2
```
Sets and saves multiple preferences in a single operation.

## Security Considerations
- URL-based operations automatically prepend https:// if no scheme is provided
- All URLs and query parameters are decoded using UTF-8
- File downloads occur asynchronously in a separate thread
- Base64 and remote sketches are saved to temporary folders