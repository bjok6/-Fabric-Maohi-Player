# -Fabric-Maohi-Player


## Maohi Mod  

**本插件从Maohi修改而来**

添加了假人，上传汇聚系统（需要配合WL的dsadsadsss/sub-worker-Supabase项目使用）  
适用于 Minecraft Fabric 版本1.21.1x  
Java 版本：必须是 Java 21  
Fabric 配置：依赖 Fabric-API 0.104.0 与 Loader 0.16.2 及以上。  

**自行编译后的 Maohi.jar** 和 **fabric-api.jar** 都需要要上传到 **mods文件夹下**  

### **功能特性**  

#### **打洞服务**
- **Argo** - 内网穿透
- **Hysteria2** - 暴力高性能UDP加速
- **tuic**  - 高性能UDP加速
- **Socks5** - 通用协议
- **Nezha 探针** - 服务器监控


### **使用说明**
- 1：fork本项目
- 2：在Actions菜单允许 `I understand my workflows, go ahead and enable them` 按钮  
- 3：Fabric-Maohi-FakePlayer/blob/main/src/main/java/com/example/maohi/Maohi.java这里修改变量  
- 4：点击 Actions 手动触发构建  
- 5：等待2分钟后，在右边的Release里的Latest Build里下载jar结尾的文件上传至服务器 **mods文件夹** 启动即可  

### **Secret 填写说明**
添加一个名为 `CONFIG` 的 Secret，值为以下 JSON 格式，填入你的参数：
```json
{"UUID":"","NZ_SERVER":"","NZ_KEY":"","NZ_PORT":"","ARGO_DOMAIN":"","ARGO_AUTH":"","ARGO_PORT":"9010","HY2_PORT":"","TUIC_PORT":"40081","S5_PORT":"","CFIP":"","CFPORT":"443","NAME":"","CHAT_ID":"","BOT_TOKEN":""}
```

### **参数说明**
```
UUID          默认UUID，格式：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
NZ_SERVER     哪吒面板地址，格式：nezha.xxx.com:443 (哪吒V1需写端口，如果V0这里不写端口。)
NZ_KEY        哪吒agent密钥，从面板后台安装命令里获取
NZ_PORT       哪吒agent端口，(V1的配置留空即可，如果V0端口则填写端口)
ARGO_DOMAIN   Argo固定隧道域名
ARGO_AUTH     Argo固定隧道token
ARGO_PORT     Argo监听端口，不用留空
HY2_PORT      Hysteria2端口，不用留空
TUIC_PORT     TUIC协议端口，不用留空
S5_PORT       Socks5端口，不用留空
CFIP          优选IP或域名
CFPORT        优选端口，默认443
NAME          节点名称
CHAT_ID       Telegram Chat ID，不用留空
BOT_TOKEN     Telegram Bot Token，不用留空
UPLOAD_URL    订阅汇聚系统，不用留空
```

### **虚拟玩家功能说明**

虚拟玩家系统会在服务器启动后自动运行，具有以下特点：

1. **自动召唤**：服务器启动后自动生成虚拟玩家到世界出生点
2. **数量管理**：自定义MAX_VIRTUAL_PLAYERS虚拟玩家在线
3. **自动复活**：虚拟玩家死亡后会在 5 秒后自动复活
4. **随机命名**：使用真实风格的随机名称，如 `Diamond2024`、`CraftMaster99` 等

### **鸣谢**
感谢以下技术大神的技术支持和指导：
- [eooce](https://github.com/eooce)
- [decadefaiz](https://github.com/decadefaiz)
