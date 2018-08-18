This is the sqllite database used by drone when it starts up, using the `fommil` username and a known token.

The following instructions are if you want to rebuild the database for your own username and projects:

Add an OAuth App at `https://github.com/settings/developers` with callback

```
https://localhost:8000/authorize
```

Then, you set these environment variables

```
export DRONE_ADMIN=<your github username>
export DRONE_GITHUB_CLIENT=<your github client id>
export DRONE_GITHUB_SECRET=<your github client secret>
```

Un-comment the `volumes:` lines in the compose file, so that this directory
becomes a live mount (not jut a copy) when you start drone. Then delete the
existing drone.sqlite file and start drone from fresh

```
docker-compose -f docker/example-it.yml up
```

Browse to `https://localhost:8000` and do the OAuth dance to give github access
to drone. Then you should be able to see your own projects and can enable then.

While in the drone UI, grab your user token and set to `DRONE_TOKEN` instead of
the dummy one that comes with this app.

Spin down the server and persist the new database.
