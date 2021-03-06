Android Helper
=========================================================

These are useful classes that I use across some projects.

[ThumbLoader](https://github.com/mufumbo/android-helper/blob/master/src/com/mufumbo/android/helper/ThumbLoader.java)
-----------

Fast helper to download and display images asynchronously in a `ListView`. Caching is done in-memory by `SoftReference`.
 
First consumes the LAST images added to the queue and kill the queue tail.

It's useful when you are displaying thumbnails on a `ListView` and you don't want to loose time downloading 
the old elements of the list. Instead, you want to show the current elements that were last shown.

Create a `ViewHolder` for `ListView` that implements `ThumbEvent` and then when populating the `ViewHolder`:
	`thumbLoaderInstance.pushEvent(viewHolderInstanceThatImplementsThumbEvent, true);`  