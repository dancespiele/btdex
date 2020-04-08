package btdex.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import bt.BT;
import bt.compiler.Compiler;
import btdex.core.Constants;
import btdex.core.Contracts;
import btdex.core.Globals;
import btdex.core.NumberFormatting;
import btdex.sc.SellContract;
import burst.kit.crypto.BurstCrypto;
import burst.kit.entity.BurstValue;
import burst.kit.entity.response.TransactionBroadcast;
import io.reactivex.Single;

public class RegisterContractDialog extends JDialog implements ActionListener, ChangeListener {
	private static final long serialVersionUID = 1L;

	JTextPane conditions;
	JCheckBox acceptBox;

	JSpinner numOfContractsSpinner;

	JPasswordField pin;

	private JButton okButton;
	private JButton cancelButton;

	private Compiler contract;

	public RegisterContractDialog(Window owner) {
		super(owner, ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		setTitle("Register Smart Contracts");

		conditions = new JTextPane();
		conditions.setPreferredSize(new Dimension(80, 120));
		acceptBox = new JCheckBox("I accept the terms and conditions");

		// The number of contracts to register
		SpinnerNumberModel numModel = new SpinnerNumberModel(2, 1, 10, 1);
		numOfContractsSpinner = new JSpinner(numModel);
		JPanel numOfContractsPanel = new Desc("Number of contracts", numOfContractsSpinner);
		numOfContractsSpinner.addChangeListener(this);

		// Create a button
		JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));

		pin = new JPasswordField(12);
		pin.addActionListener(this);

		cancelButton = new JButton("Cancel");
		okButton = new JButton("OK");

		cancelButton.addActionListener(this);
		okButton.addActionListener(this);

		buttonPane.add(new Desc("PIN", pin));
		buttonPane.add(new Desc(" ", cancelButton));
		buttonPane.add(new Desc(" ", okButton));

		JPanel content = (JPanel)getContentPane();
		content.setBorder(new EmptyBorder(4, 4, 4, 4));

		JPanel conditionsPanel = new JPanel(new BorderLayout());
		conditionsPanel.setBorder(BorderFactory.createTitledBorder("Terms and conditions"));
		JScrollPane scroll = new JScrollPane(conditions);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setPreferredSize(conditions.getPreferredSize());
		conditionsPanel.add(scroll, BorderLayout.CENTER);

		conditionsPanel.add(acceptBox, BorderLayout.PAGE_END);

		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.add(conditionsPanel, BorderLayout.PAGE_END);

		content.add(numOfContractsPanel, BorderLayout.PAGE_START);
		content.add(centerPanel, BorderLayout.CENTER);
		content.add(buttonPane, BorderLayout.PAGE_END);

		contract = Contracts.getContract();
		stateChanged(null);
		pack();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == cancelButton) {
			setVisible(false);
		}

		if(e.getSource() == okButton || e.getSource() == pin) {
			String error = null;
			Globals g = Globals.getInstance();

			if(error == null && !acceptBox.isSelected()) {
				error = "You must accept the terms first";
				acceptBox.requestFocus();
			}

			if(error == null && !g.checkPIN(pin.getPassword())) {
				error = "Invalid PIN";
				pin.requestFocus();
			}

			if(error!=null) {
				Toast.makeText((JFrame) this.getOwner(), error, Toast.Style.ERROR).display(okButton);
				return;
			}

			// all set, lets register the contract
			try {
				setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

				int ncontracts = Integer.parseInt(numOfContractsSpinner.getValue().toString());

				for (int c = 0; c < ncontracts; c++) {
					Compiler contract = Contracts.getContract();
					long data[] = Contracts.getNewContractData(g.isTestnet());

					ByteBuffer dataBuffer = ByteBuffer.allocate(data==null ? 0 : data.length*8);
					dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
					for (int i = 0; data!=null && i < data.length; i++) {
						dataBuffer.putLong(data[i]);
					}

					byte[] creationBytes = BurstCrypto.getInstance().getATCreationBytes((short) 1,
							contract.getCode(), dataBuffer.array(), (short)contract.getDataPages(), (short)1, (short)1,
							BurstValue.fromPlanck(SellContract.ACTIVATION_FEE));

					Single<TransactionBroadcast> tx = g.getNS().generateCreateATTransaction(g.getPubKey(),
							BT.getMinRegisteringFee(contract),
							Constants.BURST_DEADLINE, "BTDEX", "BTDEX sell contract", creationBytes)
							.flatMap(unsignedTransactionBytes -> {
								byte[] signedTransactionBytes = g.signTransaction(pin.getPassword(), unsignedTransactionBytes);
								return g.getNS().broadcastTransaction(signedTransactionBytes);
							});

					TransactionBroadcast tb = tx.blockingGet();
					tb.getTransactionId();
					setVisible(false);

					Toast.makeText((JFrame) this.getOwner(),
							String.format("Transaction %s has been broadcast", tb.getTransactionId().toString()), Toast.Style.SUCCESS).display();	
				}
			}
			catch (Exception ex) {
				Toast.makeText((JFrame) this.getOwner(), ex.getCause().getMessage(), Toast.Style.ERROR).display(okButton);
			}
			setCursor(Cursor.getDefaultCursor());
		}
	}

	@Override
	public void stateChanged(ChangeEvent evt) {
		Integer ncontracts = Integer.parseInt(numOfContractsSpinner.getValue().toString());
		String terms = null;
		terms = "You are registering %s new smart contracts for selling BURST at a cost of %s BURST each.\n\n"
				+ "These contracts can be configured later to sell BURST at any market. "
				+ "Your new contracts will be available in a few minutes, as soon "
				+ "as the registration transactions confirm.";
		terms = String.format(terms, ncontracts,
				NumberFormatting.BURST.format(BT.getMinRegisteringFee(contract).longValue()));
		conditions.setText(terms);
		conditions.setCaretPosition(0);
	}
}
