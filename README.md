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

## Record schemas

The records the processors emit are defined by plain Java classes in
`nifi-email-extraction-processors`, and their Avro schemas are induced from those classes by
Avro's reflect API. The classes are the single source of truth; there are no `.avsc` files to
keep in step with them.

The build writes the schemas out for anything downstream that needs a copy, in two places:

* under `avro/` inside the processors jar
* as a standalone `nifi-email-extraction-processors-*-avro-schemas.jar`

Both are generated from the compiled classes during the build, so they cannot drift from what
the processors actually write.

## Credits and Copyright notices

Test .EML file taken from this location: https://www.phpclasses.org/browse/file/14672.html

## License

ASLv2
