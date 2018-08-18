FROM drone/drone:0.8
MAINTAINER fommil

ENV DRONE_SERVER_CERT "/etc/certs/localhost/drone.crt"
ENV DRONE_SERVER_KEY "/etc/certs/localhost/drone.key"
ENV DRONE_HOST "https://localhost:8000"
ENV DRONE_SECRET "supertopsecret"
ENV DRONE_OPEN true
ENV DRONE_GITHUB true

ADD drone.crt /etc/certs/localhost/drone.crt
ADD drone.key /etc/certs/localhost/drone.key
ADD drone.sqlite /var/lib/drone/drone.sqlite
