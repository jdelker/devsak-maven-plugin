# Developer's Swiss Army Knife for Maven

Maven plugin, which provides several useful goals for unusual requirements:

unpack:
Extract a local (!) archive file (for unpacking dependency artifacts, see maven-dependency-plugin!)

copy-with-dependencies:
Copy the given artifacts, including all it's dependencies. This basically equals
the functionality of maven-dependency-plugin, but uses a given list of artifacts
rather than the project dependencies.