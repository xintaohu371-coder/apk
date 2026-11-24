# 三角洲特勤处自动化助手（Android Kotlin 项目）

本应用基于 Android 原生 **AccessibilityService（无障碍服务）+ AlarmManager 精确闹钟 + 前台服务** 实现，无需 Root，可根据你上传的录屏流程自动完成《三角洲行动》特勤处的日常收菜与制造。

## 方式一：GitHub 云端免费一键打包 APK（推荐，无需安装 Android Studio）

本压缩包已内置 `.github/workflows/build-apk.yml` 自动构建脚本，按以下步骤约 2 分钟即可下载安装包：

1. 解压本压缩包，得到 `DeltaForceAutoHelper` 文件夹。
2. 登录 [GitHub](https://github.com/)，点击右上角 `+` -> **New repository** 创建一个新仓库（可设为 Private 私有仓库）。
3. 将 `DeltaForceAutoHelper` 文件夹内的**所有文件和隐藏文件夹**（注意必须包含 `.github/workflows/build-apk.yml`）推送到该仓库：
   - 若使用命令行：
     ```bash
     cd DeltaForceAutoHelper
     git init
     git add .
     git commit -m "init"
     git branch -M main
     git remote add origin https://github.com/<你的用户名>/<你的仓库名>.git
     git push -u origin main
     ```
   - 若直接用网页上传：先在仓库里上传除 `.github` 外的文件，再点击 **Add file -> Create new file**，路径填 `.github/workflows/build-apk.yml`，把压缩包里该文件的内容粘贴进去提交即可。
4. 打开仓库顶部的 **Actions** 标签页，等待 `Build Android APK` 变为绿色对勾（约 1–2 分钟）。
5. 点进该构建记录，在页面底部的 **Artifacts** 区域点击 **`DeltaForceAutoHelper-APK`** 下载压缩包，解压后即可得到可安装到 Android 手机的 **`app-debug.apk`**。

## 方式二：本地 Android Studio 编译

1. 使用 **Android Studio** 打开解压后的 `DeltaForceAutoHelper` 目录。
2. 等待 Gradle 同步完成（JDK 17 + Android SDK 34）。
3. 点击菜单栏 **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** 导出安装包。

## 已内置的自动化流程（按录屏标定）

1. 自动启动《三角洲行动》（默认国服包名 `com.tencent.tmgp.dfm`，可在高级设置修改）
2. 等待游戏加载完成后，点击模式界面左下角 **「前往游玩 >>」**
3. 等待进入大厅，点击右上角 **X** 关闭弹窗广告（次数可配置，默认 1 次）
4. 点击主大厅底部 **「特勤处」**
5. 依次点击已勾选制造台（默认：**工作台、制药台、防具台**）领取成品，并点击底部空白跳过「获得奖励」
6. 依次进入各制造台 -> 选中对应列表项（工作台第 2 项 `4.6x30mm AP SX`、制药台第 1 项 `战地医疗箱`、防具台第 1 项 `H09 防暴头盔`）-> 点击「生产」-> 若弹出「一键补齐清单」自动点击底部绿色按钮购买 -> 再次点击「生产」开始新一轮制造 -> 返回特勤处
7. 返回主大厅并记录日志

## 手机端权限与使用提示

- **开启无障碍与后台保活**：打开 App 后依次点击 **「开启无障碍」** 和 **「忽略电池优化」**，并在系统管家中允许后台自启动。
- **定时模式**：支持 **每 7 小时 30 分钟循环**（默认）与 **每天 07:30 固定** 两种模式，可在首页直接切换。
- **锁屏与账号风险**：无人值守定时触发时系统锁屏密码会拦截启动，建议在备用机关闭锁屏密码；同时自动化点击工具可能涉及游戏服务条款与反作弊检测风险，请自行评估后使用。
