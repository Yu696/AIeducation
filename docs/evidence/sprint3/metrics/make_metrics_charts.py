# -*- coding: utf-8 -*-
"""
T-07 项目级度量图表生成脚本。

输出：
    sprint-velocity.png    三轮吞吐：产品故事 / 技术任务 / 自动化用例 / 冒烟检查
    cumulative-burnup.png  累计交付燃起图 + 自动化资产增长

用法：
    python docs/evidence/sprint3/metrics/make_metrics_charts.py
"""
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "Arial Unicode MS", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False

OUT_DIR = os.path.dirname(os.path.abspath(__file__))

SPRINTS = ["Sprint 1", "Sprint 2", "Sprint 3"]
STORIES = [6, 4, 1]          # Must 故事交付数
TASKS = [0, 1, 6]             # 技术/发布任务包（Sprint 2: T-01；Sprint 3: T-02—T-07）
TESTS = [81, 108, 109]        # 自动化用例累计规模
SMOKE = [56, 56, 60]          # 真实 MySQL 冒烟项


def velocity_chart():
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.8), dpi=150)
    x = range(len(SPRINTS))
    width = 0.36

    ax = axes[0]
    ax.bar([i - width / 2 for i in x], STORIES, width, label="产品故事", color="#2f6fed")
    ax.bar([i + width / 2 for i in x], TASKS, width, label="技术/发布任务", color="#f2a33a")
    ax.set_xticks(list(x))
    ax.set_xticklabels(SPRINTS)
    ax.set_ylabel("交付数量（个）")
    ax.set_title("三轮交付吞吐：故事与任务")
    ax.legend(frameon=False)
    ax.grid(axis="y", alpha=0.25)
    for i, v in enumerate(STORIES):
        ax.text(i - width / 2, v + 0.15, str(v), ha="center", fontsize=9)
    for i, v in enumerate(TASKS):
        ax.text(i + width / 2, v + 0.15, str(v), ha="center", fontsize=9)

    ax = axes[1]
    ax.bar([i - width / 2 for i in x], TESTS, width, label="自动化用例", color="#2e9d68")
    ax.bar([i + width / 2 for i in x], SMOKE, width, label="真实 MySQL 冒烟", color="#8a63d2")
    ax.set_xticks(list(x))
    ax.set_xticklabels(SPRINTS)
    ax.set_ylabel("累计检查项（个）")
    ax.set_title("三轮质量资产：自动化用例与冒烟")
    ax.legend(frameon=False)
    ax.grid(axis="y", alpha=0.25)
    for i, v in enumerate(TESTS):
        ax.text(i - width / 2, v + 1.5, str(v), ha="center", fontsize=9)
    for i, v in enumerate(SMOKE):
        ax.text(i + width / 2, v + 1.5, str(v), ha="center", fontsize=9)

    fig.tight_layout()
    path = os.path.join(OUT_DIR, "sprint-velocity.png")
    fig.savefig(path)
    plt.close(fig)
    print("written:", path)


def burnup_chart():
    cumulative_items = []
    running = 0
    for s, t in zip(STORIES, TASKS):
        running += s + t
        cumulative_items.append(running)
    target = cumulative_items[-1]

    fig, ax1 = plt.subplots(figsize=(10, 5), dpi=150)
    ax1.plot(SPRINTS, cumulative_items, marker="o", linewidth=2.4,
             color="#2f6fed", label="累计交付（故事+任务）")
    ax1.axhline(target, color="#2f6fed", linestyle="--", linewidth=1, alpha=0.5)
    ax1.set_ylim(0, target + 3)
    ax1.set_ylabel("累计交付工作项（个）")
    ax1.set_title("累计燃起图与自动化资产增长")
    ax1.grid(axis="y", alpha=0.25)
    for i, v in enumerate(cumulative_items):
        ax1.annotate(str(v), (i, v), textcoords="offset points", xytext=(0, 8),
                     ha="center", fontsize=9, color="#2f6fed")

    ax2 = ax1.twinx()
    ax2.plot(SPRINTS, TESTS, marker="s", linewidth=2, color="#2e9d68",
             label="自动化用例累计")
    ax2.plot(SPRINTS, SMOKE, marker="^", linewidth=2, color="#8a63d2",
             label="冒烟检查累计")
    ax2.set_ylabel("质量资产（个）")
    ax2.set_ylim(0, max(TESTS) * 1.25)

    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(lines1 + lines2, labels1 + labels2, loc="lower right", frameon=False)

    fig.tight_layout()
    path = os.path.join(OUT_DIR, "cumulative-burnup.png")
    fig.savefig(path)
    plt.close(fig)
    print("written:", path)


if __name__ == "__main__":
    velocity_chart()
    burnup_chart()
