#!/data/data/tech.ula/files/support/busybox sh

checkForServer() {
    echo "checking $1 $($LIB_PATH/busybox ps -o pid,comm | $LIB_PATH/busybox awk -v pid="$1" '$1 == pid { print $2 }')"
    case "$($LIB_PATH/busybox ps -o pid,comm | $LIB_PATH/busybox awk -v pid="$1" '$1 == pid { print $2 }')" in
        *dropbear*)
            echo "found dropbear"
            exit 0
            ;;
        *vnc*)
            echo "found vnc"
            exit 0
            ;;
        *twm*)
            echo "found twm"
            exit 0
            ;;
    esac
    
    for cpid in $($LIB_PATH/busybox ps -o ppid,pid | $LIB_PATH/busybox awk -v pid="$1" '$1 == pid { print $2 }') 
    do
        checkForServer $cpid
    done
}

if [[ $# == 0 ]]; then
    echo "usage: $(basename $0) <top pid to check>"
    exit 1
fi

for pid in $*
do
    checkForServer $pid
done
exit 1
