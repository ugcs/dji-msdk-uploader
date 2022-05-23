# dji-msdk-uploader

Application for diagnostic DJI MSDK issues related to route uploading.


## Why does this repository exists?

From time to time, UgCS users encounter with the route uploading issues. This repository contains a simple applicatsion that deserialize [WaypointV2Mission](https://developer.dji.com/api-reference/android-api/Components/Missions/DJIWaypointV2Mission.html?search=waypointv2mission&i=0&) from .json file, and uploads the mission to the drone. The goal of this application is to explain the issue to the DJI Support team as fast as possible so thay can help us find a solution. 

## How does this work?

1. When a mission uploading fails, UgCS for DJI serialize the mission to a .json file.
2. We reproduce the issue with the dji-msdk-uploader to make sure that the issue is not in the UgCS for DJI.
3. We create a separate branch with the .json file that contains a mission causing the issue.
4. We send request to DJI MSDK Support team, attaching the link to the branch.
5. Now DJI Support team can reproduce the issue with the debugger attached, ensure that the issue not in the our application, and advice us how to resolve the issue.
