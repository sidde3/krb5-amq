INTERVAL=10

while [ true ]
do
    jstack $1 >> thread-dump.txt
    sleep $INTERVAL
done