#!/bin/bash

# GET THE CURRENT TERMINAL DEVICE
current=$(tty | cut -d/ -f3-)

# 'ps -A -o tty' lists all processes with their associated terminal device.
# 'grep pts/' filters out only the lines that contain 'pts/' in the output of the previous command.
# 'grep -v "$current"' filters out the line that matches the current terminal device, stored in the 'current' variable.

# shellcheck disable=SC2009
all=$(ps -A -o tty | grep pts/ | grep -v "$current")

for i in $all; do
    pkill -9 -t "$i"
done
# Iterate over each terminal device in the 'all' variable.
# 'pkill' is used to send a signal (-9 indicates the SIGKILL signal) to all processes associated with the specified terminal device (-t option).
# The processes associated with each terminal device are terminated.

