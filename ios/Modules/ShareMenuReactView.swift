//
//  ShareMenuReactView.swift
//  RNShareMenu
//
//  Created by Gustavo Parreira on 28/07/2020.
//

import MobileCoreServices
import AVFoundation

@objc(ShareMenuReactView)
public class ShareMenuReactView: NSObject {
    static var viewDelegate: ReactShareViewDelegate?
    
    let shareDispatchGroup = DispatchGroup()
    
    @objc
    static public func requiresMainQueueSetup() -> Bool {
        return false
    }

    public static func attachViewDelegate(_ delegate: ReactShareViewDelegate!) {
        guard (ShareMenuReactView.viewDelegate == nil) else { return }

        ShareMenuReactView.viewDelegate = delegate
    }

    public static func detachViewDelegate() {
        ShareMenuReactView.viewDelegate = nil
    }

    @objc(dismissExtension:)
    func dismissExtension(_ error: String?) {
        guard let extensionContext = ShareMenuReactView.viewDelegate?.loadExtensionContext() else {
            print("Error: \(NO_EXTENSION_CONTEXT_ERROR)")
            return
        }

        if error != nil {
            let exception = NSError(
                domain: Bundle.main.bundleIdentifier!,
                code: DISMISS_SHARE_EXTENSION_WITH_ERROR_CODE,
                userInfo: ["error": error!]
            )
            extensionContext.cancelRequest(withError: exception)
            return
        }

        extensionContext.completeRequest(returningItems: [], completionHandler: nil)
    }

    @objc
    func openApp() {
        guard let viewDelegate = ShareMenuReactView.viewDelegate else {
            print("Error: \(NO_DELEGATE_ERROR)")
            return
        }

        viewDelegate.openApp()
    }

    @objc(continueInApp:)
    func continueInApp(_ extraData: [String:Any]?) {
        guard let viewDelegate = ShareMenuReactView.viewDelegate else {
            print("Error: \(NO_DELEGATE_ERROR)")
            return
        }

        let extensionContext = viewDelegate.loadExtensionContext()

        guard let item = extensionContext.inputItems.first as? NSExtensionItem else {
            print("Error: \(COULD_NOT_FIND_ITEM_ERROR)")
            return
        }

        viewDelegate.continueInApp(with: item, and: extraData)
    }

    @objc(data:reject:)
    func data(_
            resolve: @escaping RCTPromiseResolveBlock,
            reject: @escaping RCTPromiseRejectBlock) {
        guard let extensionContext = ShareMenuReactView.viewDelegate?.loadExtensionContext() else {
            print("Error: \(NO_EXTENSION_CONTEXT_ERROR)")
            return
        }

        extractDataFromContext(context: extensionContext) { (data, error) in
            guard (error == nil) else {
                reject("error", error?.description, nil)
                return
            }
            resolve([DATA_KEY: data])
            //resolve([MIME_TYPE_KEY: mimeType, DATA_KEY: data])
        }
    }

    func extractDataFromContext(context: NSExtensionContext, withCallback callback: @escaping (NSMutableArray, NSException?) -> Void) {
        let item:NSExtensionItem! = context.inputItems.first as? NSExtensionItem
        let attachments:[AnyObject]! = item.attachments

        var results : NSMutableArray = []
        
        var urlProvider:NSItemProvider! = nil
        
        var textProvider:NSItemProvider! = nil
        var dataProvider:NSItemProvider! = nil

        for provider in attachments {
            self.shareDispatchGroup.enter()
            
            if provider.hasItemConformingToTypeIdentifier(kUTTypeURL as String) {
                urlProvider = provider as? NSItemProvider
                urlProvider.loadItem(forTypeIdentifier: kUTTypeURL as String, options: nil) { (item, error) in
                    let url: URL! = item as? URL
                    if url.absoluteString.hasPrefix("file://") {
                        let dict: NSMutableDictionary = [:]
                        dict["url"] = url.absoluteString
                        dict["mimeType"] = self.extractMimeType(from: url)
                        dict["thumbnail"] = nil
                        dict["Id"] = UUID().uuidString
                        
                        results.add(dict)
                        self.shareDispatchGroup.leave()
                    }
                }
                //break
            } else if provider.hasItemConformingToTypeIdentifier(kUTTypeText as String) {
                textProvider = provider as? NSItemProvider
                //break
            } else if provider.hasItemConformingToTypeIdentifier(kUTTypeImage as String) {
                var imageProvider:NSItemProvider! = nil
                imageProvider = provider as? NSItemProvider
                imageProvider.loadItem(forTypeIdentifier: kUTTypeImage as String, options: nil) { (item, error) in
                    let url: URL! = item as? URL
                    
                    let dict: NSMutableDictionary = [:]
                    dict["url"] = url.absoluteString
                    dict["mimeType"] = self.extractMimeType(from: url)
                    dict["thumbnail"] = nil
                    dict["Id"] = UUID().uuidString
                    
                    results.add(dict)
                    self.shareDispatchGroup.leave()
                }
                //break
            }  else if provider.hasItemConformingToTypeIdentifier(kUTTypeMovie as String) {
                var videoProvider:NSItemProvider! = nil
                videoProvider = provider as? NSItemProvider
                videoProvider.loadItem(forTypeIdentifier: kUTTypeMovie as String, options: nil) { (item, error) in
                    let url: URL! = item as? URL
                    
                    var thumbBase64: String = ""
                    var thumbImage: UIImage? = self.thumbnailForVideo(url: url)
                    if thumbImage != nil {
                        thumbImage = thumbImage?.resizeImage(CGFloat.init(500.0), opaque: false)
                        let thumbImageData:NSData = thumbImage!.jpegData(compressionQuality: 0.8)! as NSData
                        thumbBase64 = thumbImageData.base64EncodedString(options: .lineLength64Characters)
                    }
                    
                    let dict: NSMutableDictionary = [:]
                    dict["url"] = url.absoluteString
                    dict["mimeType"] = self.extractMimeType(from: url)
                    dict["thumbnail"] = thumbBase64
                    dict["Id"] = UUID().uuidString
                    
                    results.add(dict)
                    self.shareDispatchGroup.leave()
                }
                //break
            } else if provider.hasItemConformingToTypeIdentifier(kUTTypeData as String) {
                dataProvider = provider as? NSItemProvider
                //break
            }
        }
        
        self.shareDispatchGroup.notify(queue: .main) {
            callback(results, nil)
        }

//        if (urlProvider != nil) {
//            urlProvider.loadItem(forTypeIdentifier: kUTTypeURL as String, options: nil) { (item, error) in
//                let url: URL! = item as? URL
//
//                callback(url.absoluteString, "text/plain", nil)
//            }
//        } else if (imageProvider != nil) {
//            imageProvider.loadItem(forTypeIdentifier: kUTTypeImage as String, options: nil) { (item, error) in
//                let url: URL! = item as? URL
//
//                callback(url.absoluteString, self.extractMimeType(from: url), nil)
//            }
//        } else if (textProvider != nil) {
//            textProvider.loadItem(forTypeIdentifier: kUTTypeText as String, options: nil) { (item, error) in
//                let text:String! = item as? String
//
//                callback(text, "text/plain", nil)
//            }
//        }  else if (dataProvider != nil) {
//            dataProvider.loadItem(forTypeIdentifier: kUTTypeData as String, options: nil) { (item, error) in
//                let url: URL! = item as? URL
//
//                callback(url.absoluteString, self.extractMimeType(from: url), nil)
//            }
//        } else {
//            callback(nil, nil, NSException(name: NSExceptionName(rawValue: "Error"), reason:"couldn't find provider", userInfo:nil))
//        }
    }

    func thumbnailForVideo(url: URL) -> UIImage? {
        let asset = AVAsset(url: url)
        let assetImageGenerator = AVAssetImageGenerator(asset: asset)
        assetImageGenerator.appliesPreferredTrackTransform = true

        var time = asset.duration
        time.value = min(time.value, 2)

        do {
            let imageRef = try assetImageGenerator.copyCGImage(at: time, actualTime: nil)
            return UIImage(cgImage: imageRef)
        } catch {
            print("failed to create thumbnail")
            return nil
        }
    }
    
    func extractMimeType(from url: URL) -> String {
      let fileExtension: CFString = url.pathExtension as CFString
      guard let extUTI = UTTypeCreatePreferredIdentifierForTag(
              kUTTagClassFilenameExtension,
              fileExtension,
              nil
      )?.takeUnretainedValue() else { return "" }

      guard let mimeUTI = UTTypeCopyPreferredTagWithClass(extUTI, kUTTagClassMIMEType)
      else { return "" }

      return mimeUTI.takeUnretainedValue() as String
    }
}

extension UIImage {
    func resizeImage(_ dimension: CGFloat, opaque: Bool, contentMode: UIView.ContentMode = .scaleAspectFit) -> UIImage {
        var width: CGFloat
        var height: CGFloat
        var newImage: UIImage

        let size = self.size
        let aspectRatio =  size.width/size.height

        switch contentMode {
            case .scaleAspectFit:
                if aspectRatio > 1 {                            // Landscape image
                    width = dimension
                    height = dimension / aspectRatio
                } else {                                        // Portrait image
                    height = dimension
                    width = dimension * aspectRatio
                }

        default:
            fatalError("UIIMage.resizeToFit(): FATAL: Unimplemented ContentMode")
        }

        if #available(iOS 10.0, *) {
            let renderFormat = UIGraphicsImageRendererFormat.default()
            renderFormat.opaque = opaque
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: renderFormat)
            newImage = renderer.image {
                (context) in
                self.draw(in: CGRect(x: 0, y: 0, width: width, height: height))
            }
        } else {
            UIGraphicsBeginImageContextWithOptions(CGSize(width: width, height: height), opaque, 0)
                self.draw(in: CGRect(x: 0, y: 0, width: width, height: height))
                newImage = UIGraphicsGetImageFromCurrentImageContext()!
            UIGraphicsEndImageContext()
        }

        return newImage
    }
}
