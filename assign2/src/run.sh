#!/bin/bash

# Function to handle Ctrl+C (SIGINT signal)
function ctrl_c() {
    echo "Stopping server and terminating..."
    # shellcheck disable=SC2046
    kill $(jobs -p) >/dev/null 2>&1
    exit 0
}

# Set the signal handler
trap ctrl_c SIGINT

while getopts ":drtu" opt; do
  case ${opt} in
    d ) # Delete all .class files
        find . -name "*.class" -type f -delete
      ;;
  t ) # Register Test

        # Erase all content of file for easier repeated testing
        truncate -s 0 main/data/users.txt

        fuser -k 1234/tcp; clear; javac main/Server.java; java main.Server unranked 2 2 2 &

        # Wait for two seconds
        sleep 2

        # Open 2 new terminals and run Client
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register zeDiogo apple1; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register eduSilva apple2; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register afonsoFarroco apple3; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register suzanaDiogo apple4; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register dianaMeireles apple5; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register sergioDiogo apple6; exec bash"

        wait
      ;;

    r ) # Play Test for 2 games of 2 players each in ranked mode

        # Requirements ->
            # - 4 registered users
            # - Groups of users must have close ranking numbers, so we can see the matchmaking algorithm working, for example:
              # - zeDiogo (1) and eduSilva (2) -> Group 1 -> 1200 & 1261
              # - afonsoFarroco (3) and suzanaDiogo (4) -> Group 2 -> 1100 & 1150

        fuser -k 1234/tcp; clear; javac main/Server.java; java main.Server ranked 2 3 4 &

        # Wait for two seconds
        sleep 2

        # Open 2 new terminals and run Client
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client login zeDiogo apple1; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client login eduSilva apple2; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client login afonsoFarroco apple3; exec bash"
#        gnome-terminal -- bash -c "javac main/Client.java && java main/Client login suzanaDiogo apple4; exec bash"

        wait
      ;;

    u ) # Play Test for 2 games of 2 players each in ranked mode

            # Requirements ->
                # - 4 registered users
                # - Groups of users must have close ranking numbers, so we can see the matchmaking algorithm working, for example:
                  # - zeDiogo (1) and eduSilva (2) -> Group 1 -> 1200 & 1261
                  # - afonsoFarroco (3) and suzanaDiogo (4) -> Group 2 -> 1100 & 1150

            fuser -k 1234/tcp; clear; javac main/Server.java; java main.Server unranked 2 2 2 &

            # Wait for two seconds
            sleep 2

            # Open 2 new terminals and run Client
            gnome-terminal -- bash -c "javac main/Client.java && java main/Client login zeDiogo apple1; exec bash"
            gnome-terminal -- bash -c "javac main/Client.java && java main/Client login eduSilva apple2; exec bash"
            gnome-terminal -- bash -c "javac main/Client.java && java main/Client login afonsoFarroco apple3; exec bash"
#            gnome-terminal -- bash -c "javac main/Client.java && java main/Client login suzanaDiogo apple4; exec bash"

            wait
          ;;

    \? ) echo "Usage: myscript.sh [-t || -u || -r || -d
         exit 1
      ;;
  esac
done
shift $((OPTIND -1))
