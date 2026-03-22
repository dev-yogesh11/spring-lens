#!/bin/bash
SPRINGLENS_URL="http://localhost:8087/api/v1/ai/chat/query"

QUESTIONS=(
  "What are the specific ownership or control thresholds defined for identifying a Beneficial Owner when the customer of an NBFC is a company, a partnership firm, or an unincorporated association?"
  "Under the RBI KYC Directions 2025, what is the prescribed periodicity for NBFCs to carry out periodic updation of KYC for high, medium, and low-risk customers?"
  "What are the mandatory timelines and requirements for an NBFC regarding the capturing and uploading of customer KYC records onto the Central KYC Records Registry?"
  "According to the Risk Management chapter, what specific parameters must an NBFC use to undertake risk categorisation of its customers?"
  "What are the transaction limits and operational restrictions for Small Accounts opened by NBFCs where full CDD has not been completed?"
  "Under Section 20(1) of the Banking Regulation Act 1949, what are the primary statutory restrictions regarding advances against a bank own shares and advances to its directors?"
  "What are the statutory limits and restrictions defined in Section 19 of the Banking Regulation Act 1949 regarding a bank holding shares in other companies?"
  "What is the threshold for loans and advances to relatives of directors that requires prior sanction from the Board of Directors or Management Committee?"
  "What specific restrictions apply to banks regarding the financing of industries that produce or consume Ozone Depleting Substances?"
  "What are the regulatory restrictions and operational stipulations for bank advances against Sensitive Commodities under Selective Credit Control?"
  "What are the specific regulatory requirements and timelines for card-issuers regarding the closure of a credit card account upon a customer request?"
  "How must card-issuers calculate and communicate interest rates and what are the mandatory disclosures regarding the Minimum Amount Due to prevent negative amortization?"
  "What are the rules regarding the modification of the billing cycle and the treatment of credit amounts arising from reversed or failed transactions?"
  "What are the four types of Priority Sector Lending Certificates and what specific sub-targets or targets does each type count toward?"
  "What are the operational guidelines regarding the transfer of risk, expiry dates, and lot sizes for the trading of Priority Sector Lending Certificates?"
  "How is the short-term credit limit for the first year calculated for a farmer cultivating a single crop under the Kisan Credit Card scheme?"
  "What are the specific security and margin requirements including the thresholds for waiving collateral for loans sanctioned under the Kisan Credit Card scheme?"
  "What are the primary goals of the Reserve Bank of India Communication Policy and how does the Bank customize its dissemination strategy for different target audiences?"
  "What is the total Priority Sector Lending target for Regional Rural Banks as a percentage of their Adjusted Net Bank Credit and what is the specific sub-target for Small and Marginal Farmers?"
  "What are the eligibility criteria for housing loans to be classified under the Priority Sector for RRBs and what are the specific limits for repair loans in rural versus town areas?"
)

GROUND_TRUTHS=(
  "For a company, the threshold is ownership of or entitlement to more than 10 percent of the shares, capital, or profits. For a partnership firm, it is more than 10 percent of the capital or profits. For an unincorporated association or body of individuals, it is more than 15 percent of the property, capital, or profits."
  "NBFCs must carry out periodic updation at least once every two years for high-risk customers, once every eight years for medium-risk customers, and once every ten years for low-risk customers from the date of opening the account or the last KYC updation."
  "The NBFC shall capture a customer KYC records and upload them onto the CKYCR within 10 days of the commencement of an account-based relationship. For individual accounts, this applies to those opened on or after April 1, 2017, and for Legal Entities, those opened on or after April 1, 2021."
  "Risk categorisation must be based on parameters such as the customer identity, social and financial status, nature of business activity, information about the customer business and its location, geographical risk, type of products and services offered, delivery channels used, and types of transactions undertaken such as cash, cheque, wire transfers, or forex."
  "Small accounts remain operational for 12 months. Balances in all accounts together shall not exceed 50000 rupees at any point, total credit shall not exceed 1 lakh in a year, and the NBFC must notify the customer when the balance reaches 40000 rupees or annual credit reaches 80000 rupees."
  "A bank cannot grant any loans and advances on the security of its own shares. Banks are prohibited from entering into any commitment for granting any loans or advances to or on behalf of any of its directors, or any firm in which any of its directors is interested as partner, manager, employee, or guarantor."
  "Under Section 19(2), a bank shall not hold shares in any company of an amount exceeding 30 percent of the paid-up share capital of that company or 30 percent of its own paid-up share capital and reserves, whichever is less. Section 19(3) prohibits banks from holding shares in any company in the management of which any managing director or manager of the bank is in any manner concerned or interested."
  "Unless sanctioned by the Board of Directors or Management Committee, banks should not grant loans and advances aggregating Rupees twenty-five lakhs and above to any relative other than spouse and minor or dependent children of their own Chairmen, Managing Directors or other Directors, or to any firm or company in which such relatives hold substantial interest."
  "Banks should not extend finance for setting up new units consuming or producing Ozone Depleting Substances such as Chlorofluorocarbon-11 for foam products or CFC-12 for refrigerators. No financial assistance should be extended to small or medium scale units engaged in the manufacture of aerosol units using CFC."
  "The RBI issues directives restricting advances against sensitive commodities which include food grains, major oilseeds, raw cotton, sugar, and cotton textiles. Banks must ensure that such credit facilities do not defeat the purpose of the directives and should not consider advances against book debts or receivables in favour of such borrowers."
  "Any request for closure of a credit card shall be honoured by the card-issuer within seven working days, subject to payment of all dues by the cardholder. Failure to complete the process within seven working days shall result in a penalty of 500 rupees per day of delay payable to the cardholder provided there is no outstanding in the account."
  "Card-issuers must quote Annualized Percentage Rates for various scenarios and explain the calculation method with clear examples. The Minimum Amount Due must be calculated such that it does not result in negative amortization, meaning it should cover at least the interest, taxes, and other charges."
  "Cardholders must be provided the option to modify their credit card billing cycle at least once. Any credit from refund, failed, or reversed transactions before the payment due date must be immediately adjusted against the payment due. For credits exceeding one percent of the credit limit or 5000 rupees the issuer must seek explicit consent within seven days."
  "The four types are: PSLC Agriculture counting towards the agriculture target and overall PSL target; PSLC SF/MF counting towards Small and Marginal Farmers sub-target, Weaker Sections sub-target, agriculture target, and overall PSL target; PSLC Micro Enterprises counting towards the micro-enterprise sub-target and overall PSL target; PSLC General counting towards the overall PSL target."
  "In the trading of PSLCs there is no transfer of risks or loan assets. All PSLCs expire on March 31st and are not valid beyond that reporting date. PSLCs have a standard lot size of 25 lakh rupees and multiples thereof. Trading is conducted through the RBI CBS portal e-Kuber."
  "The short-term limit for the first year is arrived at by multiplying the scale of finance for the crop by the extent of the area cultivated. To this, 10 percent of the limit is added for post-harvest and household requirements, and another 20 percent is added for repairs and maintenance expenses of farm assets. Costs for crop insurance and asset insurance are also included."
  "Banks are required to waive margin and security requirements for KCC limits up to 1 lakh rupees. For KCC limits up to 3 lakh rupees where there is a tie-up for recovery, banks may sanction loans based solely on the hypothecation of crops without insisting on collateral security. Collateral security may be obtained at the bank discretion for loan limits above 1 lakh rupees in non-tie-up cases."
  "The principal goals include providing clarity on its roles and responsibilities, building confidence in policy measures, improving transparency and accountability, and anchoring the expectations of economic agents. The Bank customizes its communication for diverse audiences using public awareness initiatives, social media, and a dedicated microsite available in eleven major regional languages in addition to Hindi and English."
  "The total Priority Sector Lending target for Regional Rural Banks is 75 percent of their Adjusted Net Bank Credit or Credit Equivalent Amount of Off-Balance Sheet Exposure whichever is higher. The specific sub-target for Small and Marginal Farmers is 8 percent of ANBC or CEOBE whichever is higher."
  "Housing loans to individuals can be up to 20 lakh rupees in retrospective areas with population less than 10 lakh. Loans for repairs to damaged dwelling units are eligible up to 2 lakh rupees in rural and semi-urban areas and up to 5 lakh rupees in urban and metropolitan areas."
)

collect_for_strategy() {
    local STRATEGY=$1
    local SAFE_STRATEGY="${STRATEGY//-/_}"
    local OUTPUT_FILE="eval_${SAFE_STRATEGY}.json"
    local PAIRS_FILE="pairs_${SAFE_STRATEGY}.json"

    echo "============================================"
    echo "Collecting for strategy: $STRATEGY"
    echo "============================================"

    echo "[]" > "$PAIRS_FILE"

    for i in "${!QUESTIONS[@]}"; do
        local Q_NUM=$((i + 1))
        echo "  Question $Q_NUM/20..."

        local QUESTION="${QUESTIONS[$i]}"
        local GROUND_TRUTH="${GROUND_TRUTHS[$i]}"

        # Write request to temp file
        local REQUEST_FILE=$(mktemp /tmp/sl_req_XXXXXX.json)
        jq -n \
            --arg message "$QUESTION" \
            --arg strategy "$STRATEGY" \
            '{"message": $message, "retrievalStrategy": $strategy, "memoryEnabled": false}' > "$REQUEST_FILE"

        # Write response directly to temp file — fixes apostrophe bug
        local RESPONSE_FILE=$(mktemp /tmp/sl_resp_XXXXXX.json)
        curl -s -X POST "$SPRINGLENS_URL" \
            -H "Content-Type: application/json" \
            -d @"$REQUEST_FILE" > "$RESPONSE_FILE"
        rm -f "$REQUEST_FILE"

        # Validate response
        if ! jq empty "$RESPONSE_FILE" 2>/dev/null; then
            echo "    WARNING: Invalid response for Q$Q_NUM"
            echo '{"answer":"No answer retrieved","sources":[]}' > "$RESPONSE_FILE"
        fi

        # Build pair file directly from response file — no shell variables for content
        local PAIR_FILE=$(mktemp /tmp/sl_pair_XXXXXX.json)
        jq -n \
            --arg question "$QUESTION" \
            --arg ground_truth "$GROUND_TRUTH" \
            --arg answer "$(jq -r '.answer // "No answer retrieved"' "$RESPONSE_FILE")" \
            --argjson contexts "$(jq '[.sources[]?.fullText // ""] | map(select(. != ""))' "$RESPONSE_FILE")" \
            '{question: $question, ground_truth: $ground_truth, answer: $answer, contexts: $contexts}' > "$PAIR_FILE"

        rm -f "$RESPONSE_FILE"

        # Append pair using --slurpfile — safe against all special characters
        jq --slurpfile pair "$PAIR_FILE" '. + $pair' "$PAIRS_FILE" > "${PAIRS_FILE}.tmp"
        mv "${PAIRS_FILE}.tmp" "$PAIRS_FILE"
        rm -f "$PAIR_FILE"

        echo "    Done Q$Q_NUM — pairs so far: $(jq 'length' $PAIRS_FILE)"
        sleep 1
    done

    # Assemble final output using --slurpfile — fixes apostrophe bug in assembly
    jq -n \
        --arg strategy "$STRATEGY" \
        --slurpfile pairs "$PAIRS_FILE" \
        '{retrieval_strategy: $strategy, pairs: $pairs[0]}' > "$OUTPUT_FILE"

    rm -f "$PAIRS_FILE"
    echo "Done: $OUTPUT_FILE — $(jq '.pairs | length' $OUTPUT_FILE) pairs"
    echo ""
}

echo "Starting data collection for all 3 strategies..."
echo "This will make 60 API calls. Estimated time: 5-10 minutes."
echo ""

collect_for_strategy "vector-only"
collect_for_strategy "hybrid"
collect_for_strategy "hybrid-rerank"

echo "============================================"
echo "Collection complete. Files created:"
ls -lh eval_*.json
echo "============================================"