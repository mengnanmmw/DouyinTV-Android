package com.douyintv.app

object DouyinJsBridge {

    fun buildEnhanceScript(autoPlayNext: Boolean): String {
        val autoNextFlag = if (autoPlayNext) "true" else "false"
        return """
            (function() {
                window.__douyinTvAutoNext = $autoNextFlag;

                // Hide intrusive download / floating banners on TV screen
                const styleId = 'douyintv-inject-style';
                if (!document.getElementById(styleId)) {
                    const style = document.createElement('style');
                    style.id = styleId;
                    style.textContent = `
                        ::-webkit-scrollbar { display: none !important; }
                        [class*="download"], [class*="Download"], [class*="app-guide"] {
                            display: none !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                }

                // Hook active video ended event for auto-next
                if (!window.__douyinTvObserverInstalled) {
                    window.__douyinTvObserverInstalled = true;
                    setInterval(function() {
                        const videos = document.querySelectorAll('video');
                        videos.forEach(function(v) {
                            if (!v.dataset.dtvHooked) {
                                v.dataset.dtvHooked = '1';
                                v.addEventListener('ended', function() {
                                    if (window.__douyinTvAutoNext) {
                                        window.__douyinTvNext();
                                    }
                                });
                            }
                        });
                    }, 1200);
                }

                window.__douyinTvNext = function() {
                    const nextBtn = document.querySelector('[data-e2e="video-switch-next-arrow"], .xgplayer-playswitch-next');
                    if (nextBtn) {
                        nextBtn.click();
                        return;
                    }
                    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40, which: 40, bubbles: true }));
                };

                window.__douyinTvPrev = function() {
                    const prevBtn = document.querySelector('[data-e2e="video-switch-prev-arrow"], .xgplayer-playswitch-prev');
                    if (prevBtn) {
                        prevBtn.click();
                        return;
                    }
                    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', code: 'ArrowUp', keyCode: 38, which: 38, bubbles: true }));
                };

                window.__douyinTvTogglePlay = function() {
                    const v = Array.from(document.querySelectorAll('video')).find(el => !el.paused) || document.querySelector('video');
                    if (v) {
                        if (v.paused) {
                            v.play().catch(function(){});
                        } else {
                            v.pause();
                        }
                    } else {
                        document.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', code: 'Space', keyCode: 32, which: 32, bubbles: true }));
                    }
                };

                window.__douyinTvSeek = function(deltaSec) {
                    const v = Array.from(document.querySelectorAll('video')).find(el => el.readyState > 0);
                    if (v && isFinite(v.duration)) {
                        v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + deltaSec));
                    }
                };

                window.__douyinTvOpenLogin = function() {
                    const loginBtn = document.querySelector('#login-pannel, [data-e2e="login-button"], button[class*="login"]');
                    if (loginBtn) loginBtn.click();
                };
            })();
        """.trimIndent()
    }
}
