
import fyers.FyersFund;
import com.rj.model.FundSummary;

void main() {
    FyersFund fyersFund = new FyersFund();
    FundSummary fund = fyersFund.getFunds();
    System.out.println(fund);
    System.out.println(fund.getAvailableBalance());
    System.out.println(fund.getFundTransfer());
    System.out.println(fund.getFundLimits());
    System.out.println(fund.getAdhocLimit());
    System.out.println(fund.getUtilizedAmount());
    System.out.println(fund.getRealizedPnl());
    System.out.println(fund.getReceivables());
    System.out.println(fund.getTotalBalance());
    System.out.println(fund.getClearBalance());
}



