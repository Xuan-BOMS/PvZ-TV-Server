(() => {
  const remoteBase = "http://154.219.106.185:48080";
  const serverBase = location.hostname.endsWith("github.io") || location.port === "8000" ? remoteBase : location.origin;
  document.querySelectorAll("[data-server-path]").forEach(link => {
    link.href = new URL(link.dataset.serverPath, serverBase).href;
  });

  const downloads = document.createElement("dialog");
  downloads.className = "download-dialog";
  downloads.setAttribute("aria-labelledby", "downloadTitle");
  downloads.innerHTML = `<button class="dialog-close" type="button" aria-label="关闭下载窗口">×</button>
    <div class="download-heading"><img src="./site/ui/download-versus.webp" width="119" height="119" alt="">
    <h2 id="downloadTitle">游戏下载</h2></div>
    <div class="download-links"></div><p class="download-note">下载链接待补充</p>`;
  const list = downloads.querySelector(".download-links");
  for (const item of window.PVZ_DOWNLOADS || []) {
    const row = document.createElement("div");
    row.className = "download-row";
    const mark = document.createElement("span");
    mark.className = "drive-mark";
    mark.textContent = item.mark;
    const info = document.createElement("span");
    info.className = "drive-info";
    const name = document.createElement("strong");
    name.textContent = item.name;
    const detail = document.createElement("small");
    const available = /^https?:\/\//i.test(item.url || "");
    detail.textContent = available ? (item.code ? `提取码：${item.code}` : "打开网盘下载") : "链接待补充";
    info.append(name, detail);
    const action = document.createElement(available ? "a" : "button");
    action.className = "drive-action";
    action.textContent = available ? "前往下载 ↗" : "待补充";
    if (available) {
      action.href = item.url;
      action.target = "_blank";
      action.rel = "noopener noreferrer";
    } else {
      action.type = "button";
      action.disabled = true;
    }
    row.append(mark, info, action);
    list.append(row);
  }
  downloads.querySelector(".download-note").hidden = (window.PVZ_DOWNLOADS || []).some(item => /^https?:\/\//i.test(item.url || ""));
  document.body.append(downloads);
  if (location.hash === "#downloads") downloads.showModal();
  document.querySelectorAll("[data-open-downloads]").forEach(button => button.addEventListener("click", event => {
    event.preventDefault();
    downloads.showModal();
  }));
  downloads.querySelector(".dialog-close").addEventListener("click", () => downloads.close());
  downloads.addEventListener("click", event => {
    const rect = downloads.getBoundingClientRect();
    if (event.target === downloads && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom)) downloads.close();
  });
})();
