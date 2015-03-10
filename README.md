# d-cent prototype

## Getting Started

###Local setup
To start the development VM you will need to install
- Vagrant + Virtualbox (see https://www.vagrantup.com/downloads.html, https://www.virtualbox.org/wiki/Downloads)
- Ansible (see http://docs.ansible.com/intro_installation.html)

```
git clone git@github.com:ThoughtWorksInc/objective8.git
cd objective8/ops/
```

### Working on the VM
####To get started:

```
vagrant up
# type 'vagrant' when asked for a sudoers password
vagrant ssh
cd /var/objective8
lein ragtime migrate
```

####Running the tests

To run all tests:
```
lein midje
```
To run only unit tests:
```
lein midje :config midje/unit_tests.clj
```
To run unit and integration tests:
```
lein midje :config midje/integration_tests.clj
```
To run only functional tests:
```
lein midje :config midje/functional_tests.clj
```

####Running the app

######Build sass using:
```
./pre-push.sh
```

######Running the app with a fake twitter (used for Sign-in) 
```
lein repl
(reset :stub-twitter)
```

###### Running the app with credentials
create a task (for example `start_with_credentials.sh` with the following content:

```
API_BEARER_NAME=<choose a bearer name>
API_BEARER_TOKEN=<choose a secure bearer token>
TWITTER_CONSUMER_TOKEN=<obtain this from twitter when registering the application to allow sign-in via twitter> \
TWITTER_CONSUMER_SECRET_TOKEN=<as above> \
BASE_URI=<either localhost or VM ip address and :APP_PORT> \
APP_PORT= <> \
DB_PORT= <> \
lein repl $*
```
then run the task and start the app using:
```
(reset :default)
```

##Deployment

To deploy, you need to set some environment variables:
```
export APP_PORT=<port on which the applicaton will be served, defaults to 8080>
export BASE_URI=<the base uri at which the application is served, including the port, defaults to 'localhost:8080'>
export TWITTER_CONSUMER_TOKEN=<obtain this from twitter when registering the application to allow sign-in via twitter>
export TWITTER_CONSUMER_SECRET_TOKEN=<as above>
```
## Docker

With root privileges:
```
docker build -t objective8 .
docker run -it -p 8080:8080 --rm --name objective8-live objective8
```
