Hanni
=====
Hanni uses SimLib (https://github.com/suddin/ca.usask.cs.srlab.simLib) to perform code clone detection in macro-based generators and generated Cobol files. Hanni creates a mapping between these clones to infer additional information.

## Setup


* Checkout SimLib using `git clone https://github.com/suddin/ca.usask.cs.srlab.simLib.git`

* Adjust `testDataPath` configuration in conf/application.conf.

* Hanni needs the play! framework (http://www.playframework.com/)

* Start Hanni with `activator run` and than open a browser and navigate to http://localhost:9000
