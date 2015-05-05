# Diego Task Scheduler

This is a prototype task scheduling system that allow you to send docker images hosted on e.g. [Docker Hub](https://hub.docker.com/) to a running instance of [Lattice](http://lattice.cf/), or anything running the [Diego Receptor API](https://github.com/cloudfoundry-incubator/receptor/blob/master/doc/README.md), for that matter.

## Installation

As this is a [Clojure](http://clojure.org/) / [Leiningen](http://leiningen.org/) project, you need Leiningen installed. This can be achieved on a Mac with [Homebrew](http://brew.sh/) like so:

```sh
brew update
brew install leiningen
```

## Running in development mode

From the home directory, run the following, replacing $YOUR_IP_HERE with the IP of your machine on your local network. This is needed to allow Lattice to keep Diego Scheduler updated about finished tasks.

```sh
PORT=8080 \
API_URL=http://192.168.11.11:8888/v1 \
CALLBACK_URL="http://$YOUR_IP_HERE:8080/taskfinished" \
lein run
```

Open a browser at [http://localhost:8080/](http://localhost:8080/). You should see a rudimentary interface for creating a Task. The defaults will result in a "Successful" docker image being downloaded and run. It will have error output in the Result column. This is because AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY have bogus values. You could replace these bogus values to have the s3copier task carry out its business properly.
