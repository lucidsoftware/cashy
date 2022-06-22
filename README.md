# Cashy
=====

Cashy is a write-once static asset manager for Amazon S3.

## Things Cashy Does:

* Allows for browsing the S3 bucket
* Allows for folder creation
* Allows for uploading of assets
* Automatically create retina versions of resized images
* Minify and lightly optimize javascript using YUI Compressor
* Minify CSS using YUI Compressor
* Allow for searching of assets added through Cashy
* Gzips assets where the gzip savings are significant
* Sets cache headers on assets automatically
* Keeps an audit log of assets that are uploaded
* Syncs with S3 to make sure search results and item information are accurate

## Things Cashy Does Not Do:

* Allow deleting of assets
* Allow renaming of assets
* Allow moving of assets
* Allow uploading of assets with the same full name (folders + name)
* Heavy optimize javascript
