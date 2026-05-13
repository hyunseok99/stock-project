import React from "react";

export type ChartType = "daily" | "weekly" | "monthly" | "yearly";

interface ChartTypeButtonProps {
  type: ChartType;
  setType: (type: ChartType) => void;
}

const ChartTypeButton: React.FC<ChartTypeButtonProps> = ({ type, setType }) => {
  const handleButtonClick = (newType: ChartType) => {
    setType(newType);
  };

  return (
    <div className="chart-type-button">
      <button
        className={type === "daily" ? "selected" : ""}
        onClick={() => handleButtonClick("daily")}
        tabIndex={-1}
      >
        일
      </button>
      <button
        className={type === "weekly" ? "selected" : ""}
        onClick={() => handleButtonClick("weekly")}
        tabIndex={-1}
      >
        주
      </button>
      <button
        className={type === "monthly" ? "selected" : ""}
        onClick={() => handleButtonClick("monthly")}
        tabIndex={-1}
      >
        월
      </button>
      <button
        className={type === "yearly" ? "selected" : ""}
        onClick={() => handleButtonClick("yearly")}
        tabIndex={-1}
      >
        년
      </button>
    </div>
  );
};

export default ChartTypeButton;
