import { EInvoiceFormat } from './api';

export const formatLabel: Record<EInvoiceFormat, string> = {
  ZUGFERD: 'ZUGFeRD',
  XRECHNUNG_CII: 'XRechnung (CII)',
  XRECHNUNG_UBL: 'XRechnung (UBL)',
  CII: 'CII-XML',
  UBL: 'UBL-XML',
};
