# Designed for uni-tests
# Copyright belongs to Shenzhen Recoda Technology Co., Ltd
# website: https://www.recodadvr.com/
# Author: Pengliang
# Email : pengliang3000@163.com
# Date  : 2025-04-23
# Remark: This is internal source code, please do not disclose it to others



# Feature list
## 1.key receiver
## 2.video/audio recording
## 3.camera operation
## 4.backup battery 
## 5.back cover detection
## 6.drop detection
## 7.camera anti-shake
## 8.ic-cut switch
## 9.tri-color light
## 10.system language switch
## 11.mass storage mode operation
## 12.laser light
## 13.alarm light
## 14.back to system launcher
## 15.face detection (not for public,need internal authority)
## 16.license plate detection.(not for public,need internal authority,only for Chinese license plate)
## 17.G-sensor
## 18.android location
## 19.audio routine 
## 20.network notification
## 21.IBodyCameraService AIDL is for controlling BodyCamera app,if you have your own app and still want to use body camera app's features

# if want to more feature code ,please contact sales


# Questions and Answers
## 1.Can camera be opened when screen is off
## Yes,sure
## 2.Can switch system language?
## Yes,but only system app can do it,ask sales for app built-in steps
# 3.Can front camera do ir-cut operation
## Yes,but it does not make sense, ir-cut is designed for Back camera
# 4.why i can't get GPS info after starting Location feature.
## put device out of room ,or find some places GPS signal is strong,and it takes seconds or minutes to get GPS info,
## there is no GMS framework in device, so can't use google location,but we has GMS framework built-in firm,please ask sales
# 5.how to trigger Drop detection
## Put device device down quickly from a certain height,try not crashing device
# 6.how to use Kingstore.runAsync()/runBoolean()/run()
## if you open Feature A in thread A ,maybe have to close Feature B in Thread B,then you 'd better using KingStore.runXXX to open and close ,and it is thread-safe


