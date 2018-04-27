# weixin_zhaocha_android
用于微信小程序大家来找茬辅助的安卓版本

## 源码下载

> 链接: <a href="https://pan.baidu.com/s/1jYPFKqz449XCQfuUpXiJlw">https://pan.baidu.com/s/1jYPFKqz449XCQfuUpXiJlw</a> 密码: xyiy

## 具体详情请看 
### https://github.com/egdw/wx_game_zhaochao

## 使用方法
1. 点击安装好apk之后.
2. 点击打开辅助
3. 打开大家来找茬点击悬浮窗进行识别(注意目前只适配了1920*1080,其他屏幕请自行适配)

## 实现方法
按照上面的算法,我把OpenCV移植到了Android端然后通过获取Root权限然后操作adb进行截图.然后分析截图.进行模拟点击.

## 麻烦点
* Opencv环境的搭建
* 手机需要ROOT
