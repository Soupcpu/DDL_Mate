# 本地语音识别测试计划

## 第一阶段：Vosk 中文小模型

模型：`vosk-model-small-cn-0.22`

放置路径：

`app/src/main/assets/vosk-model-small-cn-0.22`

解压后该目录下应包含：

- `am`
- `conf`
- `graph`
- `ivector`

测试入口：

`我的 > 测试中心 > Vosk 本地小模型测试`

测试步骤：

1. 断开网络。
2. 进入测试中心。
3. 确认模型状态显示“已内置”。
4. 点击“开始 Vosk”。
5. 说出短句，例如“明天晚上八点交数据库实验报告”。
6. 点击“停止 Vosk”。
7. 检查识别结果是否可复制。

通过标准：

- 不依赖 Google 服务。
- 不需要联网。
- 不上传原始音频。
- 短句识别能得到可用文字。
- 识别失败时页面能显示明确状态。

下一阶段候选：

- `sherpa-onnx` 中文小模型
- `FunASR / SenseVoiceSmall`
