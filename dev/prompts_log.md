# Prompts Log

## 26-10-13 19:40
analyze what we could do next. a watcher is running online that tests every
10 minutes whether anything changes

## 26-10-13 20:05
commit everything in these steps

## 26-10-14 03:45
tag ours as blocked too like https://github.com/parawanderer/OpenTagViewer

extend our README so that it explains everything as well, like at
https://github.com/parawanderer/OpenTagViewer/blob/main/README.md

## 26-10-14 04:05
Their approach seems better to me, since I do not want to run a backend at
all — that is impossible for inexperienced users anyway.

Clone https://github.com/rubo77/OpenTagViewer into /var/www/ and build our
extensions there as well, such as the live BLE radar with rolling-key
identity and whatever other cool features we added.

Create a separate branch for each feature so they can be submitted as
individual MRs.
