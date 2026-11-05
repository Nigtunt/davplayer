import SwiftUI
import ComposeApp

/// 播放器内的字幕目录浏览器：默认从视频同级目录开始，可进子目录、选字幕。
struct SubtitleBrowserView: View {
    let directory: SubtitleDirectory?
    let loading: Bool
    let onClose: () -> Void
    let onUp: () -> Void
    let onOpen: (SubtitleEntry) -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.94).ignoresSafeArea()

            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Button("关闭", action: onClose)
                    Text(directory?.path ?? "字幕")
                        .font(.system(size: 14, weight: .semibold))
                        .lineLimit(1)
                        .truncationMode(.middle)
                    Spacer()
                    if directory?.parent != nil {
                        Button("上级", action: onUp)
                    }
                }
                .foregroundColor(.white)
                .padding(16)

                Divider().background(Color.white.opacity(0.25))

                if loading {
                    Spacer()
                    ProgressView().tint(.white)
                    Spacer()
                } else if let dir = directory, !dir.entries.isEmpty {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(dir.entries.indices, id: \.self) { index in
                                let entry = dir.entries[index]
                                Button {
                                    onOpen(entry)
                                } label: {
                                    HStack(spacing: 12) {
                                        Image(systemName: entry.isDir ? "folder" : "doc.text")
                                            .foregroundColor(entry.isDir ? .yellow : .white)
                                            .frame(width: 22)

                                        Text(entry.name)
                                            .foregroundColor(.white)
                                            .lineLimit(1)

                                        Spacer()

                                        if entry.isDir {
                                            Image(systemName: "chevron.right")
                                                .foregroundColor(.gray)
                                        }
                                    }
                                    .padding(.horizontal, 18)
                                    .padding(.vertical, 12)
                                }

                                Divider().background(Color.white.opacity(0.08))
                            }
                        }
                    }
                } else {
                    Spacer()
                    Text("当前目录没有字幕或子目录")
                        .foregroundColor(.gray)
                    Spacer()
                }
            }
        }
    }
}

func authenticatedURLString(_ raw: String, user: String, pass: String) -> String {
    guard !user.isEmpty, var components = URLComponents(string: raw) else { return raw }
    components.user = user
    components.password = pass
    return components.string ?? raw
}

func parentDirectoryPath(of url: URL) -> String {
    var path = url.deletingLastPathComponent().path
    if path.isEmpty { path = "/" }
    if !path.hasSuffix("/") { path += "/" }
    return path
}
