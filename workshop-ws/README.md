# 工坊上传工作区

双击 `upload.cmd` 即可发布/更新「局内卡牌图鉴」。

## 前置

- Steam 客户端已启动并登录（游戏本体不用开）。
- 游戏与 ModTheSpire 关闭。

## 文件

| 文件 | 作用 |
|---|---|
| `content/inruncompendium.jar` | 上传内容（`scripts/build.sh` 的产物副本） |
| `config.json` | 标题、描述、更新说明、标签、可见性、工坊条目 ID |
| `image.jpg` | 封面（1024×1024） |
| `mod-uploader.jar` | 上传器本体（带可用的 steam_api64.dll） |
| `upload.cmd` | 一键上传脚本 |

更新时先换掉 `content/inruncompendium.jar`，再改 `config.json` 的 `changeNote`。
新建条目时**不要**写 `steamPublishedID` 键；上传成功后上传器会把 ID 写回文件。
