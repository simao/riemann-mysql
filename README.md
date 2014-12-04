# riemann-mysql

Periodically checks mysql for metrics and forwards them to riemann

## Installation

Still some rough edges, easiest is to clone this repo and build an
uber jar:

* `git clone <repo>`
* `lein uberjar`
* `cp target/uberjar/riemann-mysql-0.1.0-SNAPSHOT.jar .`

## Usage

`riemann-mysql` will run in the foreground and send metrics to
riemannm.

Use `--help` to get more info on supported arguments

    $ java -jar riemann-mysql-0.1.0-standalone.jar --help

## Features

Currently only two metrics are collected from mysql:

* Connection count
* Slave status, including running state and seconds behind master

More metrics are planned, but not yet available

## License

Copyright © 2014 Simão Mata

Distributed under the Eclipse Public License version 1.0
