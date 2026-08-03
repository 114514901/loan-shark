# LoanShark - 高利贷插件

一个 Paper 1.21.x 的 Bukkit 插件，提供高利贷贷款功能——利滚利、暴力催收、大运制裁。

## 功能

- **主动贷款**：通过 GUI 借款，日利率 50%，利滚利（利息计入本金继续计算利息）
- **主动还款**：通过 GUI 偿还部分或全部贷款
- **被动贷款**：余额为负数时自动提供高利贷，利息直接扣除余额
- **自动扣款**：逾期 3 游戏日后，若有余额自动扣除还款
- **大运惩罚**：逾期 3 游戏日后触发——前方生成矿车"大运"，5 秒倒计时 + 音频警告后撞向玩家，击杀后贷款减少 20% 并重置计时

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/gaolidai` | 打开贷款 GUI | `loanshark.use` |
| `/gaolidai info` | 查看贷款状态 | `loanshark.use` |
| `/loanshark` | `/gaolidai` 别名 | - |
| `/gld` | `/gaolidai` 别名 | - |

## 权限

| 权限节点 | 说明 | 默认 |
|----------|------|------|
| `loanshark.use` | 允许使用高利贷 | true |
| `loanshark.admin` | 管理员权限 | op |

## 配置

见 `config.yml`，支持自定义利率、逾期天数、借款/还款预设金额、矿车速度等。

## 依赖

- Paper 1.21.x（或兼容服务端）
- Vault（经济前置）

## 数据包

大运唱片数据包位于独立仓库：将 `loan-shark-datapack/` 复制到世界文件夹的 `datapacks/loan-shark/`，然后执行 `/minecraft:reload`。

## 资源包

音频文件已集成到汉化资源包 `yggdrasil-zh-cn` 中，作为服务器资源包下发即可启用惩罚音效。

## 使用

1. 编译：`mvn clean package` 或通过 GitHub Actions
2. 将 `LoanShark-1.0.jar` 放入 `plugins/` 目录
3. 确保已安装 Vault 及经济插件（如 CMI、EssentialsX 等）
4. 重启服务器
5. 玩家使用 `/gaolidai` 开始借（上）贷（路）
