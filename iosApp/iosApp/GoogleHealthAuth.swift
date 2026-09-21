import AuthenticationServices
import ComposeApp
import Foundation
import UIKit

/// Presents Google OAuth via `ASWebAuthenticationSession` (PKCE) and feeds the
/// client id / reversed client id + session presenter into the Kotlin bridge.
enum GoogleHealthAuth {
    private static var isRegistered = false
    private static var activeSession: ASWebAuthenticationSession?

    static func register() {
        guard !isRegistered else { return }
        isRegistered = true

        let (clientId, reversedId) = readCredentials()
        GoogleHealthAuthBridge.shared.setConfig(clientId: clientId, reversedClientId: reversedId)

        GoogleHealthAuthBridge.shared.presenter = { url, callbackScheme in
            DispatchQueue.main.async {
                present(url: url, callbackScheme: callbackScheme) { redirect in
                    GoogleHealthAuthBridge.shared.complete(redirectUrl: redirect)
                }
            }
        }
    }

    private static func readCredentials() -> (String?, String?) {
        guard let url = Bundle.main.url(forResource: "credentials", withExtension: "plist"),
              let dict = NSDictionary(contentsOf: url) as? [String: Any] else {
            return (nil, nil)
        }
        return (dict["CLIENT_ID"] as? String, dict["REVERSED_CLIENT_ID"] as? String)
    }

    private static func present(
        url: String,
        callbackScheme: String,
        completion: @escaping (String?) -> Void
    ) {
        guard let authURL = URL(string: url) else {
            completion(nil)
            return
        }
        let session = ASWebAuthenticationSession(
            url: authURL,
            callbackURLScheme: callbackScheme,
            completionHandler: { callbackURL, error in
                GoogleHealthAuth.activeSession = nil
                completion(callbackURL?.absoluteString)
            }
        )
        session.presentationContextProvider = PresentationContextProvider.shared
        session.prefersEphemeralWebBrowserSession = false
        GoogleHealthAuth.activeSession = session
        session.start()
    }
}

private final class PresentationContextProvider: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = PresentationContextProvider()

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        if let window = UIApplication.shared.connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.windows.first })
            .first {
            return window
        }
        return UIApplication.shared.windows.first ?? ASPresentationAnchor()
    }
}
