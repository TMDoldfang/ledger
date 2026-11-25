# 把專案上傳到 GitHub(新手版)

上傳之後,GitHub 會自動幫你打包 APK,App 就能「線上更新」。流程只需要做一次設定。

## 開始前:三件事
1. 到 https://github.com 註冊並登入。
2. **倉庫要設成 Public**。原因:App 讀取更新時不帶任何帳號密碼,私人倉庫讀不到。
   (程式碼也會公開;這個專案不含任何個人資料。若不想公開,告訴 Claude,有替代做法。)
3. 打開專案裡 `app/src/main/res/values/config.xml`,把
   `YOUR_GITHUB_ACCOUNT/ledger` 改成你的 **帳號/倉庫名**,例如 `myname/ledger`。

## 方法一:用 Git 指令(建議,這個專案有隱藏資料夾,用指令最不會漏檔)

### 只做一次
1. 安裝 Git:https://git-scm.com/download/win(一路按 Next)。
2. 開啟 PowerShell,輸入(換成你自己的):
```
git config --global user.name "你的名字"
git config --global user.email "你註冊 GitHub 的信箱"
```
3. 在 GitHub 右上角 **+** → **New repository**,Repository name 填 `ledger`,選 **Public**,
   **不要**勾 Add a README,按 **Create repository**。畫面上會出現網址 `https://github.com/你的帳號/ledger.git`。

### 上傳
在 PowerShell 進到專案資料夾(路徑換成你的):
```
cd C:\Users\你的名字\Documents\ledger
git init
git add .
git commit -m "第一版:語音記帳與線上更新"
git branch -M main
git remote add origin https://github.com/你的帳號/ledger.git
git push -u origin main
```
第一次 `git push` 會跳出瀏覽器請你登入 GitHub,按授權即可。

### 之後每次改完程式
```
git add .
git commit -m "說明這次改了什麼"
git push
```
**每次 push 到 main,GitHub 都會自動打包出新版本。**

## 方法二:網頁上傳(不想裝 Git)
1. 建好 Public 倉庫後,點 **uploading an existing file**。
2. 把專案資料夾**裡面的全部內容**拖進網頁(約 45 個檔案,沒超過單次 100 個的上限),按 **Commit changes**。
3. **一定要檢查**倉庫首頁有沒有 `.github` 資料夾(網頁上傳有時會漏掉以「.」開頭的資料夾)。
   沒有的話:**Add file → Create new file**,檔名欄輸入 `.github/workflows/release-apk.yml`
   (輸入斜線會自動建資料夾),把專案裡同名檔案的內容整份複製貼上,按 Commit。
   也確認 `keystore` 資料夾和裡面的 `ledger.jks` 有上傳。

## 推上去之後會發生什麼
1. 倉庫上方點 **Actions** 分頁,會看到 **Build APK and publish release** 正在跑(約 5–10 分鐘,第一次比較久)。
2. 出現綠色勾勾 = 成功。倉庫右側 **Releases** 會多一個 `v1`,裡面有 `app-release.apk`。
3. 之後每推一次,就多一個 `v2`、`v3`……

## 第一次安裝到手機(只需要這一次)
1. 手機瀏覽器開你的倉庫 → Releases → 點最新版的 `app-release.apk` 下載。
2. 允許瀏覽器「安裝未知應用程式」後安裝(Play Protect 若跳掃描提示,選繼續)。
3. 之後更新:App 的 **管理** 頁底部 → **檢查更新** → **下載更新** → **安裝**。
   (App 啟動時也會自動檢查一次,有新版會跳提示。)

## 常見問題
- **Actions 出現紅色叉叉**:點進去看是哪一步失敗。最常見是程式編譯錯誤或單元測試沒過,把錯誤訊息貼給 Claude。
- **Actions 說沒有權限建立 Release**:倉庫 **Settings → Actions → General → Workflow permissions**,選 **Read and write permissions** 後存檔,再到 Actions 重跑。
- **App 說「找不到已發佈的版本」**:倉庫不是 Public,或還沒有成功的 Release,或 `update_repo` 打錯。
- **安裝時說「與現有套件衝突」**:手機上已裝的版本簽章不同(例如之前自己用別把金鑰打包的),解除安裝後再裝。
- **`git` 不是內部或外部命令**:Git 沒裝好,安裝後重開 PowerShell。
- **push 被拒絕 / 要密碼**:GitHub 不收帳號密碼,請用瀏覽器授權;若用 Personal access token,權限要勾 `repo` 和 `workflow`。
- **Android Studio 也能做**:選單 Git → Create Git Repository → Commit → Push(會引導你連結 GitHub 帳號)。
