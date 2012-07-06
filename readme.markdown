Wanted to track my GPS coordinates in a battery-efficient fashion, without having to turn InstaMapper on and off throughout the day as I walk around. InstaMapper works great, but it keeps GPS on consistently. I also wanted to store coordinates locally (SQLite database) instead of relying on uploads to InstaMapper every N minutes.

This app is an attempt to use the accelerometer (pinging every 5 seconds) to detect movement. Only then will the app pull GPS coordinates. Currently this means GPS kicks on every 6 seconds, though I plan to make it configurable. So far the battery usage is much, much lower than InstaMapper.

2012-07-06

Finally worked through a gotcha with WakeLocks. A partial wake lock will stay alive during GPS polling, but will not keep the CPU awake during accelerometer polling. Fought with this for more than a week! The workaround is to create a screen-dim + acquire-causes-wakeup lock. All better!
