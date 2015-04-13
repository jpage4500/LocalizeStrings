# LocalizeStrings
Find iOS strings which have already been localized in Android. When found, will extract & localize for iOS.

## Build

	cd src/
	javac LocalizeStrings.java

## Run

	java LocalizeStrings <ANDROID PATH> <IOS PATH>
	- where PATH is the path to your Android project (should have AndroidManifest.xml file in it)
	- <IOS PATH> is root of iOS folder

## WARNING

This will modify iOS .m source files! Be sure to start with a clean workspace (ie: no outstanding changes) under source control or have a copy of the project backed up! That way, if a mistake is made, you can always revert easily.

