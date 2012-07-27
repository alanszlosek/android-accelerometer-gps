Wanted to track my GPS coordinates in a battery-efficient fashion, without having to turn InstaMapper on and off throughout the day as I walk around. InstaMapper works great, but it keeps GPS on consistently. I also wanted to store coordinates locally (SQLite database) instead of relying on uploads to InstaMapper every N minutes.

This app is an attempt to use the accelerometer (pinging every 30-60 seconds) to detect movement. Only then will the app pull GPS coordinates. So far the battery usage is much, much lower than InstaMapper.

2012-07-06

Finally worked through a gotcha with WakeLocks. A partial wake lock will stay alive during GPS polling, but will not keep the CPU awake during accelerometer polling. Fought with this for more than a week! The workaround is to create a screen-dim + acquire-causes-wakeup lock. All better!

2012-07-11

Next plans are to:

* Add a timeout for GPS polling
* Only store the most accurate locations. Think I can tell the OS to only return fixes with at least a certain level of accuracy.
* Perhaps: Add a map to the activity and show current location

2012-07-14

GPS timeout is mostly working. Still quirky ... too tired for any more tonight.

2012-07-24

Forgot to store the best location when GPS times out.

2012-07-26

Preparing to use standard deviation of GPS accuracies as a measure of whether we should stop polling. Thinking that if we're under 20 meters of accuracy, and std-dev is around 3, we probably won't get much better and should stop.
