#!/usr/bin/env bash

cp -f /opt/opsagent/bin/init.d_opsagent /etc/init.d/opsagent
chmod +x /etc/init.d/opsagent
chkconfig --add opsagent

chmod +x /opt/opsagent/bin/opsagent