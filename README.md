# NiFi Email Extraction Bundle

## Overview

The purpose of this bundle is to facilitate the use of NiFi as a tool to read PST and MBox files and make their contents available as Record API-compatible NiFi records.

## Requirements

* Apache NiFi 2.12.0
* Java 21 (the baseline for NiFi 2.x) to build and to run

## Building

```
mvn clean install
```

Deploy `nifi-email-extraction-nar/target/nifi-email-extraction-nar-*.nar` into the `lib` (or
`extensions`) directory of a NiFi 2.12.0 installation and restart NiFi.

## Credits and Copyright notices

Test .EML file taken from this location: https://www.phpclasses.org/browse/file/14672.html

## License

ASLv2
