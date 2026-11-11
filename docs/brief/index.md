# 読む順序

* [業務概要と用語](01-business-overview.md) - シンジケートローンの仕組み、登場人物、主要概念(Position / Transaction / Share Pie など)
* [機能要件](02-functional-requirements.md) - 機能ごとの要件(FR-xx)と業務フロー
* [業務ルール](03-business-rules.md) - 検証・計算・状態遷移・手数料のルール(BR-xx)
* [非機能要件・スコープ](04-non-functional-and-scope.md) - 品質要件(NFR-xx)、対象外、将来拡張、未確定事項
* [アクター・ロールと承認ワークフロー](06-actors-and-roles.md) - 運営 SPC・リード銀行・出資者・借り手のアクター、案件単位の立場、職位と承認経路(合議・メイントラック・報告)、機能の権限、出資者への開示範囲
* [外部システム連携](07-external-integration.md) - 連携先の区分、連携先ごとの方式(ID プロバイダ、通知、営業日、信用格付、金利指標、会計向け CSV、振替依頼データのバッチ)、送金は記録だけにすること、リトライ・サーキットブレーカー・Spring Batch の技術検証を兼ねること
* [Share Pie の数値例とテストケース](05-share-pie-examples.md) - 複数回のドローダウン・返済・利息・手数料の通しの数値例と、単体テストにするテストケース(TC-xx)

# 関連

* [アーキテクチャ文書](../arch/index.md) - 技術スタックと設計方針
* [ADR](../adr/index.md) - 設計判断の記録
