<div align="center">

# SongCue

## Hear what’s playing | 听见正在播放的歌曲

[![Stars](https://img.shields.io/github/stars/SoClear/SongCue?style=for-the-badge&logo=github)](https://github.com/SoClear/SongCue) [![Release](https://img.shields.io/github/v/release/SoClear/SongCue?style=for-the-badge)](https://github.com/SoClear/SongCue/releases/latest)

![LSPosed](https://img.shields.io/badge/Powered_by-LSPosed-blue?style=flat-square&logo=android)
[![License](https://img.shields.io/badge/License-AGPL%20v3-blue.svg?style=flat-square)](./LICENSE.txt)
[![Min SDK](https://img.shields.io/badge/Min_SDK-33-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions)
[![Language](https://img.shields.io/github/languages/top/SoClear/SongCue?style=flat-square&color=blueviolet&logo=kotlin)](https://github.com/SoClear/SongCue)
[![GitHub Downloads](https://img.shields.io/github/downloads/SoClear/SongCue/total?style=flat-square&color=blue&label=GitHub%20Downloads)](https://github.com/SoClear/SongCue/releases)
[![LSPosed Downloads](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.soclear.songcue/total?style=flat-square&color=orange&label=LSPosed%20Downloads)](https://github.com/Xposed-Modules-Repo/io.github.soclear.songcue/releases)


[English](#english) | [简体中文](#简体中文)

</div>

---

## English

**SongCue** is an LSPosed module that announces song titles and artists when tracks change, using Android’s media sessions and text-to-speech engine.

### ✨ Features

- Automatically announce song titles when tracks change.
- Announce the artist before or after the title, or leave it out.
- Pause music for the announcement and resume it afterward.
- Work with music apps through standard Android media sessions.

### 📦 Requirements & Setup

- Android 13+ (API 33+), with root access and an activated LSPosed framework supporting libxposed API 102.
- A working system-default text-to-speech engine. SongCue uses its default voice without overriding the language.
- Install SongCue, enable it in LSPosed, select your music apps as its scope, then restart those apps.
- Open SongCue to configure artist announcements.

Compatibility depends on the player’s media session implementation. Announcements may begin after playback starts; repeat-one playback is not guaranteed to trigger an announcement.

---

## 简体中文

**SongCue** 是一个 LSPosed 模块，通过 Android 媒体会话和文字转语音引擎，在切歌时自动播报歌名与歌手。

### ✨ 功能

- 切歌时自动播报歌名。
- 可在歌名前或歌名后播报歌手，也可不播报歌手。
- 播报时暂停音乐，结束后自动恢复播放。
- 通过标准 Android 媒体会话适配音乐应用。

### 📦 安装与使用

- Android 13+（API 33+），已获取 Root 权限，并启用支持 libxposed API 102 的 LSPosed 框架。
- 可正常使用的系统默认文字转语音引擎（TTS）。SongCue 使用其默认语音，不强制指定语言。
- 安装 SongCue，在 LSPosed 中启用模块并勾选音乐应用作为作用域，然后重启这些应用。
- 打开 SongCue，设置歌手播报方式。

兼容性取决于播放器的媒体会话实现。播报可能在音乐开始播放后触发，单曲循环不保证触发播报。
