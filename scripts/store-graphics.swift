// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.
//
// The Google Play feature graphic (1024 × 500): the icon and "Vzato" on the green of the
// screenshots. The icon PNG comes from scripts/store-graphics.sh (Inkscape).
//
//   swift scripts/store-graphics.swift <icon.png> <output.png>
import AppKit

let args = CommandLine.arguments
guard args.count == 3, let icon = NSImage(contentsOfFile: args[1]) else { print("usage: store-graphics.swift <icon.png> <out.png>"); exit(1) }
let w: CGFloat = 1024, h: CGFloat = 500
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(w), pixelsHigh: Int(h), bitsPerSample: 8, samplesPerPixel: 4,
                           hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
rep.size = NSSize(width: w, height: h)
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
NSGradient(starting: NSColor(srgbRed: 0x23 / 255, green: 0x8a / 255, blue: 0x50 / 255, alpha: 1),
           ending: NSColor(srgbRed: 0x14 / 255, green: 0x5a / 255, blue: 0x33 / 255, alpha: 1))!
    .draw(in: NSRect(x: 0, y: 0, width: w, height: h), angle: -90)
let side: CGFloat = 260
let iconRect = NSRect(x: 150, y: (h - side) / 2, width: side, height: side)
NSBezierPath(roundedRect: iconRect, xRadius: 58, yRadius: 58).addClip()
icon.draw(in: iconRect)
NSGraphicsContext.restoreGraphicsState()
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
let base = NSFont.systemFont(ofSize: 150, weight: .heavy)
let font = NSFont(descriptor: base.fontDescriptor.withDesign(.rounded) ?? base.fontDescriptor, size: 150) ?? base
NSAttributedString(string: "Vzato", attributes: [.font: font, .foregroundColor: NSColor.white])
    .draw(at: NSPoint(x: 460, y: (h - 180) / 2))
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[2]))
