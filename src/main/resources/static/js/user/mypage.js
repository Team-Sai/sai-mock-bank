document.addEventListener("DOMContentLoaded", async () => {
    const FILE_PREVIEW = window.location.protocol === "file:";

    const ROUTES = {
        main: FILE_PREVIEW ? "../login/login.html" : "/home",
        login: FILE_PREVIEW ? "../login/login.html" : "/login"
    };

    const API = {
        me: "/api/users/me",
        linkedAccounts: "/api/mock-bank/accounts/my",
        logout: "/api/auth/logout"
    };

    document.querySelectorAll("[data-route]").forEach(link => {
        const routeName = link.dataset.route;

        if (ROUTES[routeName]) {
            link.href = ROUTES[routeName];
        }
    });

    const errorBox = document.getElementById("mypage-error");
    const logoutButton = document.getElementById("logout-button");
    const withdrawButton = document.getElementById("withdraw-button");
    const accountError = document.getElementById("account-error");

    let sessionEnding = false;
    let hasAccountSnapshot = false;

    function redirectToLogin(required = false) {
        const query = required ? "?required=true" : "";
        window.location.replace(ROUTES.login + query);
    }

    function setText(id, value) {
        const element = document.getElementById(id);

        if (element) {
            element.textContent = value ?? "";
        }
    }

    function setProfileImage(url) {
        const targets = [
            {
                image: document.getElementById("profile-image"),
                placeholder: document.getElementById("profile-placeholder")
            },
            {
                image: document.getElementById("header-profile-image"),
                placeholder: document.getElementById("header-profile-placeholder")
            }
        ];

        targets.forEach(({image, placeholder}) => {
            if (!image || !placeholder) {
                return;
            }

            if (url) {
                image.src = url;
                image.hidden = false;
                placeholder.hidden = true;
            } else {
                image.removeAttribute("src");
                image.hidden = true;
                placeholder.hidden = false;
            }
        });
    }

    function createBankIcon() {
        return `
            <div class="bank-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="m3 10 9-6 9 6"></path>
                    <path d="M5 10v8"></path>
                    <path d="M9 10v8"></path>
                    <path d="M15 10v8"></path>
                    <path d="M19 10v8"></path>
                    <path d="M3 18h18"></path>
                    <path d="M2 21h20"></path>
                </svg>
            </div>
        `;
    }

    function renderAccounts(accounts) {
        const accountList = document.getElementById("account-list");

        if (!accountList) {
            return;
        }

        accountList.innerHTML = "";

        if (!Array.isArray(accounts) || accounts.length === 0) {
            accountList.appendChild(createEmptyAccountState());
            return;
        }

        accounts.forEach(account => {
            const item = document.createElement("div");
            item.className = "account-item";

            item.innerHTML = `
                ${createBankIcon()}
                <div class="account-content">
                    <div class="account-top">
                        <div>
                            <div class="bank-name">
                                ${escapeHtml(account.bankName)}
                            </div>
                            <div class="account-name">
                                ${escapeHtml(account.accountName)}
                            </div>
                        </div>
                        <strong class="account-balance">
                            ${formatBalance(account.balance)}원
                        </strong>
                    </div>
                    <div class="account-number">
                        ${escapeHtml(account.accountNumber)}
                    </div>
                </div>
            `;

            accountList.appendChild(item);
        });
    }

    function showAccountError() {
        accountError.textContent = "계좌 목록을 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요."
            + (hasAccountSnapshot ? " 이전 조회 결과를 표시하고 있습니다." : "");
        accountError.hidden = false;
        if (!hasAccountSnapshot) {
            document.getElementById("account-list").textContent = "계좌 목록을 불러오지 못했습니다.";
        }
    }

    function formatBalance(balance) {
        if (balance == null) {
            return "0";
        }

        return Number(balance).toLocaleString("ko-KR");
    }

    function createEmptyAccountState() {
        const wrapper = document.createElement("div");
        wrapper.className = "account-empty";

        wrapper.innerHTML = `
            <div class="account-empty-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="m3 10 9-6 9 6"></path>
                    <path d="M5 10v8"></path>
                    <path d="M9 10v8"></path>
                    <path d="M15 10v8"></path>
                    <path d="M19 10v8"></path>
                    <path d="M3 18h18"></path>
                    <path d="M2 21h20"></path>
                </svg>
            </div>
            <strong class="account-empty-title">
                보유한 계좌가 없습니다
            </strong>
            <p class="account-empty-desc">계좌를 생성하면 계좌별 잔액을 확인할 수 있습니다.</p>
            <button type="button"
                    class="account-empty-button"
                    id="connect-account-button">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <circle cx="12" cy="12" r="9"></circle>
                    <path d="M12 8v8"></path>
                    <path d="M8 12h8"></path>
                </svg>
                계좌 생성하기
            </button>
        `;

        wrapper
            .querySelector("#connect-account-button")
            ?.addEventListener(
                "click",
                handleConnectAccountClick
            );

        return wrapper;
    }

    function handleConnectAccountClick() {
        document.getElementById("createAccountButton")?.click();
    }

    function renderMyPage(rawData) {
        const data = rawData?.data ?? rawData ?? {};

        setText(
            "header-user-name",
            data.name ? `${data.name} 님` : ""
        );

        setText("member-name", data.name);
        setText("member-email", data.email);
        setText("member-birth-date", data.birthDate);
        setText("member-phone", formatPhone(data.phone));
        setText("member-token", data.userToken);
        const headerName = document.getElementById("header-user-name");
        if (headerName) headerName.hidden = !data.name;
        setText("detail-email", data.email);

        const badge = document.getElementById(
            "verification-status"
        );

        if (badge) {
            if (data.verificationStatus) {
                badge.hidden = false;
                badge.textContent = data.verificationStatus;
            } else {
                badge.hidden = true;
                badge.textContent = "";
            }
        }

        setText(
            "joined-at",
            formatDateTime(data.createdAt ?? data.joinedAt)
        );

        setText(
            "marketing-consent",
            data.marketingConsent ?? ""
        );

        setProfileImage(data.profileImageUrl);
    }

    // =========================================
    // FDS 알림 기록 정리
    // =========================================

    function clearFdsNotificationStorage() {
        const keys = [];

        for (let i = 0; i < sessionStorage.length; i++) {
            const key = sessionStorage.key(i);

            if (
                key === "fdsReviewNotifications" ||
                key?.startsWith("fdsReviewNotifications:")
            ) {
                keys.push(key);
            }
        }

        keys.forEach(key => {
            sessionStorage.removeItem(key);
        });
    }

    // =========================================
    // 관리자 인증 쿠키 제거
    // =========================================

    async function clearAdminCookie() {
        if (FILE_PREVIEW) {
            return;
        }

        try {
            const response = await fetch(API.logout, {
                method: "POST",
                credentials: "same-origin",
                cache: "no-store"
            });

            if (!response.ok) {
                console.warn(
                    "[Auth] 관리자 쿠키 제거 실패 - HTTP",
                    response.status
                );
            }

        } catch (error) {
            console.warn(
                "[Auth] 로그아웃 API 호출 실패",
                error
            );
        }
    }

    // =========================================
    // 클라이언트 로그인 정보 정리
    // =========================================

    function clearLoginSession() {
        window.stopFdsNotificationStream?.();

        clearFdsNotificationStorage();

        sessionStorage.removeItem("accessToken");

        localStorage.removeItem("saiwonjangPreviewAuth");
        localStorage.removeItem("saiwonjangCurrentUser");
    }

    /**
     * 로그아웃, 인증 만료, 회원 탈퇴 성공 시
     * 서버 관리자 쿠키와 클라이언트 로그인 정보를 함께 정리한다.
     */
    async function endSession(required = false) {
        if (sessionEnding) {
            return;
        }

        sessionEnding = true;

        if (logoutButton) {
            logoutButton.disabled = true;
        }

        try {
            // HttpOnly 쿠키는 서버 응답으로만 만료 가능
            await clearAdminCookie();

        } finally {
            // 서버 요청 실패 여부와 관계없이 로컬 세션 정리
            clearLoginSession();
            redirectToLogin(required);
        }
    }

    // =========================================
    // 마이페이지 데이터 조회
    // =========================================

    async function loadMyPage() {
        if (FILE_PREVIEW) {
            loadPreviewMyPage();
            return;
        }

        const token = sessionStorage.getItem("accessToken");

        if (!token) {
            await endSession(true);
            return;
        }

        const headers = {
            Accept: "application/json",
            Authorization: `Bearer ${token}`
        };

        const [meResult, accountsResult] = await Promise.allSettled([
            fetch(API.me, {
                method: "GET",
                headers
            }),
            fetch(API.linkedAccounts, {
                method: "GET",
                headers
            })
        ]);

        const meResponse = meResult.status === "fulfilled" ? meResult.value : null;
        const accountsResponse = accountsResult.status === "fulfilled" ? accountsResult.value : null;

        if (
            meResponse?.status === 401 ||
            meResponse?.status === 403 ||
            accountsResponse?.status === 401 ||
            accountsResponse?.status === 403
        ) {
            await endSession(true);
            return;
        }

        if (!meResponse) throw new Error("내 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        const meData = await readJson(meResponse);

        if (!meResponse.ok) {
            throw new Error(
                meData.message ||
                "내 정보 조회에 실패했습니다."
            );
        }

        renderMyPage(meData);

        if (!accountsResponse?.ok) {
            showAccountError();
            return;
        }

        try {
            const accountsData = await readJson(accountsResponse);
            const accounts = accountsData?.data ?? accountsData;
            if (!Array.isArray(accounts)) {
                showAccountError();
                return;
            }
            renderAccounts(accounts);
            hasAccountSnapshot = true;
            accountError.hidden = true;
            accountError.textContent = "";
        } catch {
            showAccountError();
        }
    }

    function loadPreviewMyPage() {
        const auth = localStorage.getItem(
            "saiwonjangPreviewAuth"
        );

        const member = JSON.parse(
            localStorage.getItem("saiwonjangCurrentUser") ||
            "null"
        );

        if (!auth || !member) {
            redirectToLogin(true);
            return;
        }

        renderMyPage(member);
        renderAccounts(member.accounts ?? []);
        hasAccountSnapshot = true;
    }

    // =========================================
    // 로그아웃
    // =========================================

    async function logout() {
        await endSession(false);
    }

    // =========================================
    // 회원 탈퇴
    // =========================================

    async function withdraw() {
        const confirmed = window.confirm(
            "정말 회원 탈퇴하시겠습니까?"
        );

        if (!confirmed) {
            return;
        }

        if (FILE_PREVIEW) {
            localStorage.removeItem("saiwonjangDemoMember");
            await endSession(false);
            return;
        }

        const token = sessionStorage.getItem("accessToken");

        if (!token) {
            await endSession(true);
            return;
        }

        const response = await fetch(API.me, {
            method: "DELETE",
            headers: {
                Authorization: `Bearer ${token}`
            }
        });

        if (
            response.status === 401 ||
            response.status === 403
        ) {
            await endSession(true);
            return;
        }

        if (!response.ok) {
            const responseData = await readJson(response);

            throw new Error(
                responseData.message ||
                "회원 탈퇴에 실패했습니다."
            );
        }

        window.alert("회원 탈퇴가 완료되었습니다.");

        await endSession(false);
    }

    // =========================================
    // 이벤트 연결
    // =========================================

    logoutButton?.addEventListener("click", async () => {
        await logout();
    });

    withdrawButton?.addEventListener("click", async () => {
        if (sessionEnding) {
            return;
        }

        try {
            await withdraw();

        } catch (error) {
            console.error(error);

            window.alert(
                error.message ||
                "회원 탈퇴 처리 중 오류가 발생했습니다."
            );
        }
    });



    document.addEventListener("account-created", async () => {
        errorBox.hidden = true;
        try { await loadMyPage(); }
        catch (error) {
            errorBox.textContent = error.message || "계좌 목록을 불러오지 못했습니다.";
            errorBox.hidden = false;
        }
    });

    try {
        await loadMyPage();

    } catch (error) {
        console.error(error);

        if (errorBox) {
            const accountList = document.getElementById("account-list");
            if (accountList?.textContent === "계좌를 불러오는 중…") {
                accountList.textContent = "계좌 목록을 불러오지 못했습니다.";
            }
            errorBox.textContent =
                error.message ||
                "내 정보를 불러오지 못했습니다.";

            errorBox.hidden = false;
        }
    }

    // =========================================
    // 공통 유틸
    // =========================================

    async function readJson(response) {
        const text = await response.text();

        if (!text) {
            return {};
        }

        try {
            return JSON.parse(text);
        } catch {
            return {};
        }
    }

    function formatPhone(phone) {
        if (!phone) {
            return "";
        }

        const numbers = String(phone).replace(/\D/g, "");

        if (numbers.length === 11) {
            return numbers.replace(
                /(\d{3})(\d{4})(\d{4})/,
                "$1-$2-$3"
            );
        }

        return phone;
    }

    function formatDateTime(value) {
        if (!value) {
            return "";
        }

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return new Intl.DateTimeFormat("ko-KR", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit"
        }).format(date);
    }

    function escapeHtml(value) {
        if (value == null) {
            return "";
        }

        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }
});
