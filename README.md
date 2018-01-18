# 简介
在介绍项目之前，首先说明下我做这套示例代码的初衷。刚开始只是为了测试下在Android平台进行RTMP推流，后来发现要实现这一功能的方法很多，同时涉及的理论和技术体系很庞大，因此出了一系列的文章——[流媒体](https://www.jianshu.com/nb/17697147)，至于为什么要自己写文章，因为我发现在出现问题时候在网上寻找的答案良莠不齐，或者有的答案已经过时，有时候会折磨我很久，我想到可能有许多朋友和我一样会走这些坑，所以写这些文章记录一下，希望可以帮助到大家。涉及代码的文章都会对应有同步的[realease](https://github.com/EricLi22/FFmpegSample/releases/)版本代码，大家在阅读时一定要注意下载正确版本的代码。

项目以及文章的目的是和大家一起研究探讨RTMP推流设计的技术和原理，以及如何移植到Android平台下。整个项目的代码只是做了功能的实现，对应稳定性和性能后期会进行。这个项目无法直接给大家提供完好的车轮，只能提供一个车轮的雏形和制造车轮的思路，重点在于基础知识的掌握。如果需要用在公司的项目中，需要大家更进一步优化。

项目中有很多不完善的地方，希望大家可以提出一起讨论。更多的希望对这块领域有兴趣的朋友可以一起参与进来，共同进步。感谢大家的支持！

​	——Eric

# 开发环境

- Android Studio 3.0
- c++库编译环境 `CentOS Linux release 7.4.1708 (Core)`
- 测试机器 arm  Android 4.4.2 (如果发现项目运行失败，请注意系统是否需要**动态权限**,cpu是否是**arm处理器**)


### 注意：

- 因为我的测试机是4.4.2，我没用做动态权限，如果大家测试机系统版本高，可以手动加上动态权限。
- so库只使用了支持arm处理器。如果处理器是×86或者其他的处理器，可以自己再编译。


# 相关文章

### 软件环境

- [RTMP服务器搭建(crtmpserver和nginx)](https://www.jianshu.com/p/c71cc39f72ec)
- [Linux下glibc升级](https://www.jianshu.com/p/1f434d0c11c3)
- [Linux环境变量介绍和区别](https://www.jianshu.com/p/5fed6bb29328)



### 理论基础

- [音视频编码相关名词详解](https://www.jianshu.com/p/c398754e5984)
- [流媒体解码及H.264编码推流](https://www.jianshu.com/p/f83ef0a6f5cc)
- [flv格式详解+实例剖析](https://www.jianshu.com/p/7ffaec7b3be6)
- [基于FFmpeg进行RTMP推流（一）](https://www.jianshu.com/p/69eede147229)
- [基于FFmpeg进行RTMP推流（二）](https://www.jianshu.com/p/6b9ab2652147)



### 项目涉及文章

- [Linux下FFmpeg编译以及Android平台下使用](https://www.jianshu.com/p/4037297d883d)—[源码v1.0](https://github.com/EricLi22/FFmpegSample/releases/tag/v1.0)


- [Android平台下使用FFmpeg进行RTMP推流（视频文件推流)](https://www.jianshu.com/p/dcac5da8f1da)—[源码v1.1](https://github.com/EricLi22/FFmpegSample/releases/tag/v1.1)
- [Android平台下使用FFmpeg进行RTMP推流（摄像头推流）](https://www.jianshu.com/p/462e489b7ce0)—[源码v1.2.1](https://github.com/EricLi22/FFmpegSample/releases/tag/1.2.1)
- [Android RTMP推流之MediaCodec硬编码一（H.264进行flv封装）](https://www.jianshu.com/p/e607e63fb78f)—[源码v1.3](https://github.com/EricLi22/FFmpegSample/releases/tag/v1.3)
- [Android平台下RTMPDump的使用](https://www.jianshu.com/p/3ee9e5e4d630)—[源码v1.4](https://github.com/EricLi22/FFmpegSample/releases/tag/v1.4)
- [Android RTMP推流之MediaCodec硬编码二（RTMPDump推流）](https://www.jianshu.com/p/53ddf0831d2c)-[源码v1.5](https://github.com/EricLi22/FFmpegSample/releases/tag/v1.5)
- [MediaCodec进行AAC编解码（文件格式转换）](https://www.jianshu.com/p/875049a5b40f)-[源码v1.6](https://github.com/EricLi22/FFmpegSample/releases/tag/v1.6)
- [MediaCodec进行AAC编解码（AudioRecord采集编码）](https://www.jianshu.com/p/e32ec8a8df41)-[源码v1.7](https://github.com/EricLi22/FFmpegSample/releases/tag/v1.7)


