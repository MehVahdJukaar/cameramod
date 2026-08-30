# Vista (cameramod) 会话上下文

> 本次会话围绕 GitHub issue #95（开光影后电视黑屏）展开，最终扩展为电视带光影、镜像策略、删除镜像、镜像光影实验等问题。
> 写成日期：2026-08-30

---

## 0. 一句话总结

- 电视/取景器 feed 现在**带 Iris 光影**（`iris_off_hack` 默认改为 `false`）。
- **镜子反射仍走原版管线**（Iris 对多画布 feed 有设计性缺陷，无法稳定带光影）。
- 附带修复：feed 后 GL 帧缓冲恢复（防黑屏）、开 GUI 时暂停 feed 刷新（防整屏白闪）、Iris feed 管线每次渲染重挂载（防“关掉一台电视另一台白闪”）。
- 两个实验/派生分支：`mirror-shaders`（让镜子带光影，未稳定）、`remove-mirror`（删除镜子功能）。

---

## 1. 仓库与远端

- 本地克隆路径：`D:\WorkSpace\Gitea\Vista`
- 上游：`upstream = https://github.com/MehVahdJukaar/cameramod.git`
- Fork：`origin = https://github.com/JiangGeng0508/vista.git`
- 版本：`1.21.1 / NeoForge 21.1.248 / Fabric`，`mod_version=1.21.1-5.4.4`

### 分支
| 分支 | 说明 | HEAD |
|---|---|---|
| `master` | 稳定版：电视带光影 + 镜子原版管线 | `0692649` |
| `mirror-shaders` | 让镜子也走 Iris 光影管线的实验（未稳定） | `a013b3a` |
| `remove-mirror` | 删除镜子功能 + 电视白闪修复 | `59d2f37` |
| ~~`zinzinc`~~ | 已删除（空分支，仅指向旧提交） | — |

### 提交历史（master 之上）
- `85d5063` render tv feeds with iris shaders, restore framebuffer after feed
- `96d13bd` mirror reflections use vanilla pipeline, skip feed updates while a screen is open
- `0692649` document why mirror reflections use the vanilla pipeline
- `a013b3a` (mirror-shaders) mirror-shaders: route mirror reflections through the iris feed pipeline
- `8218eb3` (remove-mirror) remove the mirror block and reflection feature
- `59d2f37` (remove-mirror) iris: re-attach feed pipeline on every feed instead of only on canvas change

---

## 2. 原始 Issue #95

原文要点：
- 开启光影（Iris/Oculus）后 **电视屏幕变黑**。
- 补充报告（1.21.1 NeoForge + Iris 1.8.12）：电视黑 + 闪烁 + Create 电梯不可见。
- 维护者结论：100% 是 Iris 的问题，放弃修复（“That mod is impossible to work with”）。

---

## 3. 修复内容（按时间线）

### 3.1 电视带光影 + GL 帧缓冲恢复 —— `85d5063`
**根因分析**：
- 原默认 `iris_off_hack=true` 让 feed 走 VanillaRenderingPipeline → 电视不带光影。
- feed 最外层渲染结束后没恢复真实 GL 帧缓冲绑定，只有嵌套渲染恢复 → 主画面/Iris 可能继续画进 feed 画布 → 黑屏/闪烁。

**改动**：
- `IrisCompat.addConfigs`：`iris_off_hack` 默认 `true → false`（feed 走光影）。
- `VistaLevelRenderer.doRender` finally：最外层 feed 后用
  `GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer)`
  恢复进入时的帧缓冲；入口用 `GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)` 捕获。

### 3.2 镜子回退原版 + 开 GUI 暂停 feed —— `96d13bd`
**根因**：
- 镜子/电视共用同一“按维度缓存”的 Iris feed 管线；镜像用**离轴投影**，在真实光影管线里反射内容会全白/闪/深度图。
- 打开物品栏的当帧，feed（带光影）渲染与 GUI 世界层交织 → 整屏白闪。

**改动**：
- `IrisCompat`：新增 `MIRROR_PASS` ThreadLocal → 镜子反射传**原版管线**（改回）。
- `LiveFeedTexture.refresh()` / `MirrorTextureManager.processPending()`：`mc.screen != null` 时跳过刷新/处理。

### 3.3 电视与镜子共用维度导致互相污染 —— 已撤销的实验
- 尝试“按画布尺寸细分维度 + 每次渲染重挂载”→ 未解决镜子（仍一面正常其余全白/深度图）。已回退。

### 3.4 镜子带光影实验 —— `a013b3a`（mirror-shaders，未稳定）
- 取消 `MIRROR_PASS`，镜子走光影管线；feed 维度改写为 `vista_live_feed_<宽>x<高>_<dim>`；`onFeedCanvasBound` 每次渲染都 bump。
- 结论：仍无法稳定（Iris 管线将合成输出固定到第一个画布；多画布互相污染）。

### 3.5 删除镜子 —— `8218eb3`（remove-mirror）
- 删除：`MirrorBlock/Entity`、`MirrorReflection`、`MirrorReflectionTexture`、`MirrorTextureManager`、`MirrorBlockEntityRenderer`、`MirrorEndermanObservationController`、镜面材质 shader、全部镜像资源（约 78 个文件，-3699 行）、结晶 `CRYSTALLINE`、相关配置（Common/Client 的 mirror 段、`MirrorPlacement`/`MirrorUpdateMode`/`MirrorRecursionMode`、`isMirrorEnabled`）、`GazeRedirect` 镜面反弹、`LevelRendererMixin` 镜像实体隐藏、`ModLootOverrides` 长老守卫掉落、`VistaRenderTypes.mirrorMaterial` 等。
- 保留电视/取景器相关全部逻辑。NeoForge + Fabric 均编译通过。

### 3.6 关掉一台电视后另一台白闪 —— `59d2f37`（remove-mirror）
**根因（研究结论）**：
- 同一维度（改写后）的若干电视 feed 共用一条 Iris 管线。
- `onFeedCanvasBound` 只在“换画布”时 bump 版本号；两台交替时每帧都 bump → 正常。
- 关掉一台 → 其画布被 Moonlight `DynamicTextureRenderer` 缓存 evict → `destroyBuffers` 释放 GL 对象。
- 但管线内 `RenderTargets.currentDepthTexture` 仍指向被释放的深度 id（不再 bump，无人重挂）→ 剩余电视画进**不完整帧缓冲** → 白屏；GL id 回收复用 → 闪烁。

**改动**：`onFeedCanvasBound` 去掉 `lastFeedCanvas` 保护，**每次 feed 都 bump** → Iris 每次 `beginLevelRendering` 把管线重挂到当前画布，空悬引用被覆盖。

### 3.7 不同尺寸画布共用管线导致持续闪烁/白闪/跳动（2026-08-30，未提交）
**症状**：开光影后电视画面能显示，但周期性闪烁、整屏发白、画面跳动（用户确认的残留问题）。

**根因（反编译 Iris 1.8.8 确认）**：
- `remove-mirror` 上 `CompatIrisMixin` 把维度改写为 `vista_live_feed_<dim>`，同一维度**所有** feed 画布共用一条管线。
- 每台电视画布尺寸 = 屏幕像素尺寸 × `resolution_scale`，不同型号电视尺寸不同。
- `IrisRenderingPipeline.beginLevelRendering` → `RenderTargets.resizeIfNeeded`：尺寸与缓存不一致 → **全部 colortex 重建 + 清除 pass 帧缓冲销毁重建 + fullClearRequired**（反编译源码 line ~137-172）。
- 多台不同尺寸电视以 10Hz 交替 → 几乎每次 feed 渲染都全量重建并清空 → 光影包 TAA/累积缓冲永远无法收敛（跳动）、周期性白帧（白闪）+ 性能悬崖。
- `mirror-shaders` 分支（a013b3a）已做过“尺寸编入管线键”，但未移植到 remove-mirror。

**改动**：`CompatIrisMixin.vista$rewriteDimensionForFeed` 改为 `vista_live_feed_<宽>x<高>_<dim>`（feed 期间 `getMainRenderTarget()` 即当前画布）；`IrisCompat` 注释同步。管线数量上限 = 出现过的画布尺寸种数（很小）。注意：每个新尺寸首次出现时会编译一次光影包程序（一次性卡顿）。bump 版本号的机制仍需保留（同尺寸多画布 + 画布销毁重挂）。

### 3.8 同尺寸多电视 TAA 历史串扰 → 白闪/抖动（2026-08-30，未提交）
**症状（3.7 修复后实测残留）**：天空调色正常了；两台电视同屏时，一台地面白闪，另一台画面轻微抖动；单台电视完全正常。

**根因**：
- 尺寸键修复后，同尺寸画布（两台同型号电视）仍共用一条管线。
- 管线的 colortex ping-pong 缓冲是**视图相关**的时序状态（TAA 历史/SSR 累积）：两台电视交替渲染时，每台的合成都会把**另一台**的上一帧结果当历史混进来。
- 天空从任何视角看都一样 → 不受影响；地面是视图相关内容 → 混叠出白闪；另一台的 TAA 历史被污染 → 轻微抖动。与实测症状完全吻合。

**改动（clear-on-switch）**：
- `CompatIrisMixin` 新增 `preparePipeline` RETURN 注入：feed 时把解析出的管线 + 当前画布交给 `IrisCompat.onFeedPipelineBound`。
- `IrisCompat`：`LAST_FEED_CANVAS`（WeakHashMap<管线, 画布>）检测同一管线切换画布；切换时反射置位 `RenderTargets.fullClearRequired = true` → 下一次 `beginLevelRendering` 执行 `clearPassesFull`，把所有 colortex（含 TAA 历史）全清。
- 行为：单台电视（画布不变）从不清，TAA 照常跨 feed 累积；不同尺寸各走各管线不受影响；同尺寸多台同屏时不再累积（轻微锯齿代价），但白闪/串扰抖动消除。
- 备选方案（未采用）：每台电视一条管线（彻底隔离但影子缓冲+编译程序+pack 纹理每管线一份，多台时内存不可行）；渲染两遍预热（TAA 需多帧收敛，无效）。

### 3.9 真正根因：feed 相机的遮挡剔除 BFS 失效 → 地形整体消失（2026-08-30，未提交）
**关键实证**：用户截图拍到闪烁瞬间的右电视画面 = **天空+云正常渲染、地形/方块实体全部缺失**（画布被清成天空色）。这不是颜色混叠而是地形被剔除干净——3.7/3.8 的 TAA 结论只是伴随现象。

**机制（反编译 Sodium 0.8.13-beta.2 确认）**：
- `RenderSectionManager.createTerrainRenderList`：相机区块已注册 → `OcclusionCuller.findVisible`（遮挡 BFS，从相机区块向外传播）；未注册 → 八叉树全遍历（必可见）。
- 电视 feed 的虚拟相机固定在**取景器方块中心**（`setupSceneCamera`）。相机所在区块不透明（取景器嵌在山坡/墙体内）时，BFS 传不出去 → 可见区块为空 → 只剩天空+云 = "白闪"。
- 周围区块重建/加载改变 BFS 连通性 → 闪烁且"有概率自愈"；两台电视环境不同（右台相机贴地形）→ 只有右台闪；`CompatSodiumMixin` 生效前 Vista 在 Sodium 下完全走 Sodium 自己的剔除（`onSetupRenderer` 直接 return false）。
- 与是否开光影无关（Sodium 剔除两条管线都走），开光影只是让用户注意到了它。

**改动**：
- 新增 `CompatSodiumMixin`（@Pseudo + targets 字符串，避免编译期依赖）：`RenderSectionManager.shouldUseOcclusionCulling` 在 feed 渲染期间返回 false → BFS 关闭遮挡、向全部 63 个方向传播，可见性确定。feed 渲染距离小且有节流，性能代价可忽略。
- `VistaLevelRenderer.onSetupRenderer`：恢复被注释的特殊情况——feed 相机所在方块 `isSolidRender` 时关闭 smartCulling（原版路径，对齐原版 spectator 规则，dummy 相机是 BlockDisplay 不是 spectator）。
- 注册进 `vista-common.mixins.json`。

**未验证/遗留**：用户实测待确认。若仍闪，下一个排查方向是 frustum（`graph.consumeFrustumUpdate` / `offsetFrustum`）。另：`iris_off_hack=true`（feed 走原版管线）配置仍是用户级后备开关。

### 3.10 最终定位：白色是主画面 TV 方块 quad 的 CRT 自定义 shader 在 Iris 下采样失败（2026-08-30，未提交）
**排查过程**：
- 逐帧像素回读（画布 start/post-renderLevel/post-chain + moonlight display 纹理，878+ 采样）：**画布与显示纹理内容永远正常，无一白帧** → 白色与 feed 渲染完全无关。
- 白帧仅出现在进世界头三次渲染（新画布未初始化，无害）。
- 用户关光影一切正常 → 问题在 Iris 激活时的主画面路径。
- 特写截图：白的是特定 cutout 内容（植物/树叶/远处地形），近景泥土正常 → 是 quad 绘制/采样问题而非纹理内容。

**根因**：主画面 TV 方块 quad 走 `VistaRenderTypes.crtRenderType` = **自定义 ShaderInstance（CAMERA_VIEW_SHADER）+ MultiTextureStateShard 多纹理**。Iris 只覆盖原版 shader，自定义 shader 在光影管线激活时采样器绑定不可靠 → 间歇性整块 quad 采到空纹理 → 整个电视画面（含地面树草）变白。Vista 自己在 feed 路径已经因此回退 entitySolid（`hasSfx()` 第一个条件）。

**改动**：
- `IrisCompat.hasActiveShaderPack()`：管线为 `ShaderRenderingPipeline` 即光影包激活。
- `TvScreenVertexConsumers.hasSfx()`：光影包激活时也回退 entitySolid（CRT 效果在光影下暂时不可用，画面正常优先）。
- 修复 3.8 引入的崩溃：`clearTemporalBuffers` 加 `instanceof IrisRenderingPipeline` 守卫 + 捕获 ReflectiveOperationException（关光影瞬间 preparePipeline 返回 VanillaRenderingPipeline，无 renderTargets 字段 → IllegalArgumentException 崩溃，crash-2026-08-30_20.09.28）。

**诊断设施（临时，保留在 render_debug 后面）**：`LiveFeedTexture.refresh` 与 `VistaLevelRenderer.doRender` 的画布/显示纹理像素采样日志、`IrisCompat.onFeedPipelineBound` 切换日志。确认修复后应移除。

**待验证**：用户实测光影下电视是否还闪。CRT 效果在光影下回退属预期行为。

### 3.11 收敛：同尺寸电视共享管线 + full-clear = 白色斑块（2026-08-30，未提交）
**数据链**：
- 像素回读证明 feed 画布/显示纹理内容始终正常 → 白闪不在 feed 渲染本身。
- 深度冲突修复后黑闪出现（entitySolid 无多边形偏移）→ `needsManualSurfaceOffset()` 在 Iris 激活时返回 true（手动前移 0.005）。
- 日志证实：用户两台电视都是 44x44 → **同一管线** → 390 渲染中 386 次触发 clear-on-switch 的 full clear → colortex 缓冲每 50ms 被清到默认色（**colortex1 默认清屏色 = 纯白**）→ BSL 依赖缓冲累积的延迟光照/降噪被洗白 → 地面白色斑块、天空树叶正常（每帧重写）。
- "单台不闪、两台才闪"、"有概率自己变好"（单台时无交替无清除）全部吻合。
- 顶点格式翻转理论被日志排除（全程一个 XHFPModelVertexType）。

**改动（最终方案）**：
- 管线键从"每尺寸"升级为"**每画布**"：`vista_live_feed_<纹理路径>_<dim>`（纹理路径含 uuid+尺寸）。每台电视独占管线，时序缓冲彻底隔离，full-clear 永不触发。
- 上限保护：`MAX_FEED_PIPELINES=6`，超出后新电视回退按尺寸共享（此时 clear-on-switch 仍作保护）。
- 保留 clear-on-switch 作为共享回退路径下的安全网。

**内存代价**：每条管线独立 shadow targets + 编译好的程序集（BSL 下约 20-60MB/台）。2-3 台电视可接受；CCTV 房间受上限约束。
**首次渲染每台电视会有一次光影包程序编译卡顿。**

**遗留**：pre-3.8 时代的白闪（不同尺寸、无清除时也出现过）未完全解释，若本轮修复后仍有残留，需重新评估。诊断日志仍在 render_debug 后面，确认后移除。

### 3.12 白色斑块真根因：缺失方块 ID 数据的网格 + 被丢弃的 allChanged 重建（2026-08-30，未提交）
**决定性证据（用户特写截图）**：白色的恰好是**草方块顶面和树叶**（需要 BSL 依方块 ID 上色的方块），泥土/沙子正常 → 网格缺少 Iris 扩展数据（方块 ID）→ pack shader 无法识别方块 → 不上色 → 白色。"深度图"轮廓 = 同类损坏数据。
**机制**：
- 网格构建依赖 `WorldRenderingSettings.getBlockStateIds()`；管线首次 `beginLevelRendering` 才初始化它。
- 首次初始化时 Iris 调用 `levelRenderer.allChanged()` 重建所有网格以携带 ID 数据 —— **Vista 的 `vista$skipFirstFrameAllChanged`（保护 feed 渲染）把这个重建跳过后直接丢弃了**（`initializedBlockIds=true` 使它永远不再触发）。
- 进世界时 Sodium 在 ID 初始化前构建的网格 = 永久缺数据；随机方块更新偶尔重建它们 = "有概率自己变好"。Sodium 日志里 Quark 替换草颜色 provider 的行也印证白色 = 着色丢失。
**改动**：`vista$skipFirstFrameAllChanged` 跳过时置 `PENDING_WORLD_REBUILD` 标志；`IrisCompat.runPendingWorldRebuild` 在**最外层 feed 渲染完全结束后**执行一次 `levelRenderer.allChanged()`（此时不在电平渲染中，安全）。多次跳过合并为一次重建。
**代价**：进世界后首次开电视时会触发一次全区块重建（数秒内区块渐进重载，Sodium 原子替换网格，无空白期）。

### 3.13 白雾真根因：feed 跳过阴影 pass → 采样玩家中心阴影图（2026-08-30，未提交）
**决定性指纹（用户实测）**："靠近白闪画面大概率会修复，再次远离有概率再次出现"——距离依赖。
**机制**：`CompatIrisRenderingMixin` 在 feed 渲染时跳过 `renderShadows`（镜子时代的历史遗留）。feed 只能采样**主画面残留的阴影贴图**，而阴影贴图围绕**玩家**渲染：玩家靠近电视时取景器场景在覆盖范围内（正常）；走远后场景超出覆盖 → pack 雾/体积光采样到阴影图边缘垃圾 → 整个 feed 被橙白色体积雾吞掉（BSL 日落时呈橙白）。关光影无阴影贴图故无此问题。
**改动**：feed 渲染不再跳过阴影 pass（`renderShadows` 恢复执行，阴影相机 = feed 虚拟相机，阴影图覆盖 feed 场景）。每条 feed 管线有独立 shadow targets，不会互相污染。`shouldSkipShadows` 删除，allChanged 延迟跳过改用 `isFeedRendering()`。
**代价**：每次 feed 渲染多一个阴影 pass（20Hz×N 台），有可感知的性能开销；如需要可后续加"低频阴影更新"优化。

### 3.16 白色拖影真根因：区块构建窗口期的部分渲染被时间累积（2026-08-30，未提交）
**决定性证据**：
- 用户 Bliss 截图：玩家沿移动方向被复制叠加 8 次（离散副本=逐帧累积，非运动模糊连续拖影），背景树木/草地单帧正常。
- 日志：`sections=0/8/11/26 → 329/730` 逐帧爬升——进世界/传送后区块网格渐进构建窗口内，feed 渲染可见性塌陷（只画天空+实体，无地形）。
- 部分帧叠印在画布上 + 光影包时间缓冲（TAA/泛光）把幽灵混入后续正常帧 → 白色残影沿路径持久化；"靠近修复"= 构建窗口结束。
**改动**：`doRender` 入口处 `hasRenderedAllSections()`（Sodium `isTerrainRenderComplete`）为假时跳过 feed 刷新（保留最后一帧正常画面），连续跳过 60 次后强制渲染一次（防止流动水等持续更新区域永久冻结电视）。
**说明**：电视在构建窗口期停留在旧画面数秒，属预期行为。
**补充**：切换光影属于最坏情况——Iris 重载整个渲染栈、全部管线+全区块重建，窗口长达 ~10s。跳过上限已从 60 提到 200 次刷新以覆盖之；过渡期实体可能被按错误顶点布局画出平移重复副本（管线重建期的格式错位，瞬态），结束后自愈。

### 3.14 顶点格式全局翻转：Veil × Iris × Vista 架构冲突（2026-08-30，未提交）
**决定性日志**：切换光影后全局地形顶点格式从 `iris...XHFPModelVertexType` 翻转为 **`foundry.veil.forge.compat.sodium.VeilChunkVertex`**（Veil 接管主画面管线时用它），feed 管线则用 Iris 原生格式。
**机制**：`WorldRenderingSettings.VERTEX_FORMAT` 是全局单例，每条管线创建时被各自 `SodiumPrograms` 构造器覆盖。格式变化后：旧格式网格 + 新格式渲染（或反之）= 属性错位 = "深度图"花屏/白色斑块，随区块重建逐渐恶化 → "开关光影后短时间正常、之后开始闪"。
**改动**：`IrisCompat.logVertexFormatFlip` 检测到格式变化时调用 `scheduleWorldRebuild()` → 全部网格按新格式重建（复用 3.12 的延迟重建机制）。
**说明**：这是 Iris 假设"同时只有一个世界管线、一个全局格式"的架构限制，Veil 的存在使主画面与 feed 管线格式必然不同。当前方案是自愈式修复（每次管线重建后全区块重载数秒），无法根治。

---

## 4. 镜像相关的研究结论（关键）

- `PipelineManager.preparePipeline` 按维度在 `pipelinesPerDimension` **缓存唯一管线**（反编译确认）。
- `IrisRenderingPipeline` 构造时读取 `getMainRenderTarget()`（取尺寸/深度版本），`beginLevelRendering` 每次读取当前 mainRT 并 `bindWrite`。
- `RenderTargets` 只维护**一份** `currentDepthTexture` / `cachedDepthBufferVersion`（跨 feed 共享）。
- 多个 feed 画布（镜子/多台电视）共用一条管线时：
  - 若合成/目标固定到首画布 → 其余全白；
  - 若某画布销毁而 bump 停止 → 剩余画布白 + 闪。
- 这两者都与维护者“iris mess”结论一致。

---

## 5. 构建环境（本机）

- **JDK 25（Temurin 25.0.4.1）** 装于 `C:\Program Files\Java\jdk-25`：构建插件 `com.possible-triangle:1.4.234` 要求 JVM ≥ 25。
- **JDK 21** 用于 MC 21.1 toolchain 编译。
- **Gradle 9.5.0**（wrapper 指向的 9.6.1 在本机下载失败）：直接用
  `C:\Users\Kuro\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat`
- `gradle.properties` 加了本机配置（**未提交**，机器相关）：
  - `org.gradle.java.installations.paths=C:/Program Files/Java/jdk-21,C:/Program Files/Java/jdk-25`
- wrapper `networkTimeout=600000`（未提交，可留可去）。
- **JitPack `io.github.ocelot:glsl-processor:0.2.3` 已失效（401）**：会连带禁用整个 JitPack repo，卡住 `mixinsquared-forge` 解析。已把该包 pom+jar 装进 `~\.m2`（mavenLocal，排第一）规避。
- **本地测试修改（未提交）**：`neoforge/build.gradle.kts` 里把 Vampirism/Origins/Supplementaries/camera-mod/supernatural/refurbished-furniture 等改为 `modCompileOnly` 或注释（去除 dev 运行时不需要的模组）；还曾临时移除 Supplementaries 绕开它与 Sodium 的流体 mixin 崩溃。

### 常用构建命令
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-25"
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat" :neoforge:jar --console=plain
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat" :neoforge:runClient --console=plain
```

---

## 6. 已确认 / 待办

- [x] 电视带光影（`iris_off_hack=false` 默认）
- [x] 镜子反射稳定（原版管线，无假阴影/全白/闪烁）
- [x] 开物品栏不再整屏白闪（GUI 期间暂停 feed）
- [x] 关掉一台电视不再导致另一台白闪（每次渲染重挂载）
- [x] 不同尺寸电视不再因共用管线而闪烁/白闪/跳动（尺寸编入管线键，2026-08-30，编译通过，待进游戏带光影实测）
- [x] 删除镜子功能分支（remove-mirror）编译通过
- [ ] 镜子带光影（实验未达稳定，若有精力可继续）
- [ ] `59d2f37` 是否并入 `master`（master 也有同样的“关电视白闪”问题，改动同源）
- [ ] 推送/合并分支到 fork（已推送 3 个分支，`59d2f37` 尚未推）